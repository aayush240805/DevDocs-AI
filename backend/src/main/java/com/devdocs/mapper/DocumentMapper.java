package com.devdocs.mapper;

import com.devdocs.dto.response.DocumentResponse;
import com.devdocs.entity.Document;
import org.springframework.stereotype.Component;

@Component
public class DocumentMapper {

	public DocumentResponse toResponse(Document document) {
		return DocumentResponse.builder()
				.id(document.getId())
				.title(document.getTitle())
				.originalFileName(document.getOriginalFileName())
				.fileType(document.getFileType())
				.fileSize(document.getFileSize())
				.processingStatus(document.getProcessingStatus())
				.pageCount(document.getPageCount())
				.chunkCount(document.getChunkCount())
				.errorMessage(document.getErrorMessage())
				.createdAt(document.getCreatedAt())
				.updatedAt(document.getUpdatedAt())
				.build();
	}
}
