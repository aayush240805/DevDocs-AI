package com.devdocs.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ChatSessionResponse {
	private final UUID id;
	private final String title;
	private final int messageCount;
	private final Instant createdAt;
	private final Instant updatedAt;
}
