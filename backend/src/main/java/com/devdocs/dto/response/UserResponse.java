package com.devdocs.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class UserResponse {
	private final UUID id;
	private final String name;
	private final String email;
	private final Instant createdAt;
}
