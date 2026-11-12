package com.devdocs.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SendMessageResponse {
	private final ChatSessionResponse session;
	private final ChatMessageResponse userMessage;
	private final ChatMessageResponse assistantMessage;
}
