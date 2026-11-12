package com.devdocs.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceReferenceResponse {
	private String documentId;
	private String documentName;
	private Integer pageNumber;
	private Integer chunkIndex;
	private Double score;
	private String excerpt;
}
