package com.devdocs.entity;

import com.devdocs.enums.DocumentStatus;
import com.devdocs.enums.FileType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "documents")
public class Document extends BaseEntity {

	@Column(nullable = false, length = 255)
	private String title;

	@Column(name = "original_file_name", nullable = false, length = 255)
	private String originalFileName;

	@Enumerated(EnumType.STRING)
	@Column(name = "file_type", nullable = false, length = 20)
	private FileType fileType;

	@Column(name = "file_size", nullable = false)
	private Long fileSize;

	@Enumerated(EnumType.STRING)
	@Column(name = "processing_status", nullable = false, length = 20)
	private DocumentStatus processingStatus;

	@Column(name = "page_count")
	private Integer pageCount;

	@Column(name = "chunk_count")
	private Integer chunkCount;

	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;
}
