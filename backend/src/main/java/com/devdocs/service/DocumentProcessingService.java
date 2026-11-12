package com.devdocs.service;

import com.devdocs.enums.FileType;
import com.devdocs.exception.AiServiceException;
import com.devdocs.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

	private final TokenTextSplitter tokenTextSplitter;
	private final VectorStore vectorStore;

	public record ExtractedPage(int pageNumber, String content) {
	}

	public record ProcessingResult(int pageCount, int chunkCount) {
	}

	public ProcessingResult processAndStore(
			byte[] fileBytes,
			FileType fileType,
			String originalFileName,
			UUID documentId,
			UUID userId,
			String documentName) {

		List<ExtractedPage> pages = extractPages(fileBytes, fileType, originalFileName);

		List<Document> pageDocuments = new ArrayList<>(pages.size());
		for (ExtractedPage page : pages) {
			Map<String, Object> metadata = new HashMap<>();
			metadata.put("documentId", documentId.toString());
			metadata.put("userId", userId.toString());
			metadata.put("documentName", documentName);
			metadata.put("pageNumber", page.pageNumber());
			pageDocuments.add(new Document(page.content(), metadata));
		}

		List<Document> chunks = tokenTextSplitter.apply(pageDocuments);
		if (chunks.isEmpty()) {
			throw new BadRequestException(
					"Document '" + originalFileName + "' produced no indexable text chunks");
		}

		for (int i = 0; i < chunks.size(); i++) {
			chunks.get(i).getMetadata().put("chunkIndex", i);
		}

		try {
			vectorStore.add(chunks);
		} catch (Exception ex) {
			throw new AiServiceException(
					"Failed to generate or store embeddings for '" + originalFileName + "'", ex);
		}

		log.info("Stored {} chunk(s) across {} page(s) for document {} (user {})",
				chunks.size(), pages.size(), documentId, userId);

		return new ProcessingResult(pages.size(), chunks.size());
	}

	public void deleteDocumentVectors(UUID documentId, UUID userId) {
		FilterExpressionBuilder builder = new FilterExpressionBuilder();
		Filter.Expression filter = builder.and(
				builder.eq("documentId", documentId.toString()),
				builder.eq("userId", userId.toString())
		).build();
		vectorStore.delete(filter);
		log.debug("Deleted vector chunks for document {} (user {})", documentId, userId);
	}

	public List<ExtractedPage> extractPages(byte[] fileBytes, FileType fileType, String originalFileName) {
		List<ExtractedPage> pages = switch (fileType) {
			case PDF -> extractPdfPages(fileBytes);
			case DOCX -> extractDocxPages(fileBytes);
			case TXT -> extractTxtPages(fileBytes);
		};

		boolean hasReadableText = pages.stream()
				.anyMatch(page -> page.content() != null && !page.content().isBlank());

		if (!hasReadableText) {
			throw new BadRequestException(
					"No readable text could be extracted from '" + originalFileName + "'");
		}

		log.debug("Extracted {} non-empty page(s) from {}", pages.size(), originalFileName);
		return pages;
	}

	private List<ExtractedPage> extractPdfPages(byte[] fileBytes) {
		try (PDDocument pdf = Loader.loadPDF(fileBytes)) {
			if (pdf.isEncrypted()) {
				throw new BadRequestException("Password-protected PDFs are not supported");
			}

			int totalPages = pdf.getNumberOfPages();
			if (totalPages == 0) {
				throw new BadRequestException("PDF document has no pages");
			}

			PDFTextStripper stripper = new PDFTextStripper();
			List<ExtractedPage> pages = new ArrayList<>();
			for (int page = 1; page <= totalPages; page++) {
				stripper.setStartPage(page);
				stripper.setEndPage(page);
				String text = stripper.getText(pdf);
				if (text != null && !text.isBlank()) {
					pages.add(new ExtractedPage(page, text.trim()));
				}
			}
			return pages;
		} catch (BadRequestException ex) {
			throw ex;
		} catch (Exception ex) {
			log.debug("Failed to parse PDF document: {}", ex.getMessage());
			throw new BadRequestException("Failed to read PDF file. Ensure it is a valid, uncorrupted PDF.");
		}
	}

	private List<ExtractedPage> extractDocxPages(byte[] fileBytes) {
		try (ByteArrayInputStream input = new ByteArrayInputStream(fileBytes);
			 XWPFDocument doc = new XWPFDocument(input);
			 XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
			String text = extractor.getText();
			if (text == null || text.isBlank()) {
				return List.of();
			}
			return List.of(new ExtractedPage(1, text.trim()));
		} catch (Exception ex) {
			log.debug("Failed to parse DOCX document: {}", ex.getMessage());
			throw new BadRequestException("Failed to read DOCX file. Ensure it is a valid Word (.docx) document.");
		}
	}

	private List<ExtractedPage> extractTxtPages(byte[] fileBytes) {
		String text = new String(fileBytes, StandardCharsets.UTF_8).trim();
		if (text.isEmpty()) {
			return List.of();
		}
		return List.of(new ExtractedPage(1, text));
	}
}
