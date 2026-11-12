package com.devdocs.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ChatAnswerResponse {
	private final String question;
	private final String answer;
	private final boolean grounded;
	private final List<SourceReferenceResponse> sources;
}
