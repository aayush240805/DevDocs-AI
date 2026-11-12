package com.devdocs.service;

import com.devdocs.dto.response.DocumentResponse;
import com.devdocs.entity.Document;
import com.devdocs.entity.User;
import com.devdocs.enums.DocumentStatus;
import com.devdocs.enums.FileType;
import com.devdocs.exception.AiServiceException;
import com.devdocs.exception.BadRequestException;
import com.devdocs.exception.ResourceNotFoundException;
import com.devdocs.mapper.DocumentMapper;
import com.devdocs.repository.DocumentRepository;
import com.devdocs.repository.UserRepository;
import com.devdocs.service.DocumentProcessingService.ProcessingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

	private static final Set<String> PDF_MIME_TYPES = Set.of("application/pdf");
	private static final Set<String> DOCX_MIME_TYPES = Set.of(
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			"application/octet-stream"
	);
	private static final Set<String> TXT_MIME_TYPES = Set.of("text/plain", "application/octet-stream");

	private final DocumentRepository documentRepository;
	private final UserRepository userRepository;
	private final DocumentProcessingService documentProcessingService;
	private final DocumentMapper documentMapper;

	@Value("${app.documents.max-size-bytes:10485760}")
	private long maxSizeBytes;

	public DocumentResponse uploadDocument(MultipartFile file, String customTitle, UUID userId) {
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("Please select a non-empty file to upload");
		}
		if (file.getSize() > maxSizeBytes) {
			throw new BadRequestException("File exceeds maximum allowed size of " + (maxSizeBytes / (1024 * 1024)) + " MB");
		}

		String originalName = sanitizeFilename(file.getOriginalFilename());
		FileType fileType = resolveAndValidateFileType(originalName, file.getContentType());
		String title = resolveTitle(customTitle, originalName);

		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));

		Document document = Document.builder()
				.title(title)
				.originalFileName(originalName)
				.fileType(fileType)
				.fileSize(file.getSize())
				.processingStatus(DocumentStatus.PROCESSING)
				.user(user)
				.build();

		document = documentRepository.save(document);

		try {
			byte[] fileBytes = file.getBytes();
			ProcessingResult result = documentProcessingService.processAndStore(
					fileBytes,
					fileType,
					originalName,
					document.getId(),
					userId,
					title
			);
			document.setPageCount(result.pageCount());
			document.setChunkCount(result.chunkCount());
			document.setProcessingStatus(DocumentStatus.COMPLETED);
			document.setErrorMessage(null);
			document = documentRepository.save(document);
			return documentMapper.toResponse(document);
		} catch (BadRequestException | AiServiceException ex) {
			cleanupPartialVectors(document.getId(), userId);
			markFailed(document, ex.getMessage());
			throw ex;
		} catch (IOException ex) {
			String message = "Failed to read uploaded file bytes";
			markFailed(document, message);
			throw new BadRequestException(message);
		} catch (RuntimeException ex) {
			cleanupPartialVectors(document.getId(), userId);
			markFailed(document, ex.getMessage());
			throw ex;
		}
	}

	@Transactional(readOnly = true)
	public List<DocumentResponse> getUserDocuments(UUID userId) {
		return documentRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
				.stream()
				.map(documentMapper::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public DocumentResponse getUserDocument(UUID documentId, UUID userId) {
		Document document = documentRepository.findByIdAndUserId(documentId, userId)
				.orElseThrow(() -> new ResourceNotFoundException("Document not found"));
		return documentMapper.toResponse(document);
	}

	@Transactional
	public void deleteDocument(UUID documentId, UUID userId) {
		Document document = documentRepository.findByIdAndUserId(documentId, userId)
				.orElseThrow(() -> new ResourceNotFoundException("Document not found"));
		documentProcessingService.deleteDocumentVectors(documentId, userId);
		documentRepository.delete(document);
	}

	private void cleanupPartialVectors(UUID documentId, UUID userId) {
		try {
			documentProcessingService.deleteDocumentVectors(documentId, userId);
		} catch (Exception cleanupEx) {
			log.warn("Best-effort vector cleanup failed for document {}: {}", documentId, cleanupEx.getMessage());
		}
	}

	private void markFailed(Document document, String errorMessage) {
		document.setProcessingStatus(DocumentStatus.FAILED);
		String safeMessage = (errorMessage == null || errorMessage.isBlank())
				? "Document processing failed"
				: errorMessage;
		document.setErrorMessage(safeMessage);
		documentRepository.save(document);
	}

	private String sanitizeFilename(String rawFilename) {
		if (rawFilename == null || rawFilename.isBlank()) {
			throw new BadRequestException("Filename is required");
		}
		return Paths.get(rawFilename).getFileName().toString().trim();
	}

	private FileType resolveAndValidateFileType(String filename, String contentType) {
		String lower = filename.toLowerCase(Locale.ROOT);
		String normalizedMime = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();

		if (lower.endsWith(".pdf")) {
			if (!normalizedMime.isEmpty() && !PDF_MIME_TYPES.contains(normalizedMime) && !"application/octet-stream".equals(normalizedMime)) {
				throw new BadRequestException("Invalid MIME type for PDF file");
			}
			return FileType.PDF;
		}
		if (lower.endsWith(".docx")) {
			if (!normalizedMime.isEmpty() && !DOCX_MIME_TYPES.contains(normalizedMime)) {
				throw new BadRequestException("Invalid MIME type for DOCX file");
			}
			return FileType.DOCX;
		}
		if (lower.endsWith(".txt")) {
			if (!normalizedMime.isEmpty() && !TXT_MIME_TYPES.contains(normalizedMime)) {
				throw new BadRequestException("Invalid MIME type for TXT file");
			}
			return FileType.TXT;
		}
		throw new BadRequestException("Unsupported file type. Allowed formats: PDF, DOCX, TXT");
	}

	private String resolveTitle(String customTitle, String originalFilename) {
		if (customTitle != null && !customTitle.isBlank()) {
			String trimmed = customTitle.trim();
			return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
		}
		int dotIdx = originalFilename.lastIndexOf('.');
		String base = dotIdx > 0 ? originalFilename.substring(0, dotIdx) : originalFilename;
		return base.length() > 255 ? base.substring(0, 255) : base;
	}
}
