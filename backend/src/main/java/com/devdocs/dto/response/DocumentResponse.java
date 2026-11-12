package com.devdocs.dto.response;

import com.devdocs.enums.DocumentStatus;
import com.devdocs.enums.FileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class DocumentResponse {
	private final UUID id;
	private final String title;
	private final String originalFileName;
	private final FileType fileType;
	private final Long fileSize;
	private final DocumentStatus processingStatus;
	private final Integer pageCount;
	private final Integer chunkCount;
	private final String errorMessage;
	private final Instant createdAt;
	private final Instant updatedAt;
}
