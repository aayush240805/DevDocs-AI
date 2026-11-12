package com.devdocs.dto.response;

import com.devdocs.enums.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ChatMessageResponse {
	private final UUID id;
	private final UUID sessionId;
	private final MessageRole role;
	private final String content;
	private final Boolean grounded;
	private final List<SourceReferenceResponse> sources;
	private final Instant createdAt;
}
