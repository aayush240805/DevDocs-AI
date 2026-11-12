package com.devdocs.mapper;

import com.devdocs.dto.response.ChatMessageResponse;
import com.devdocs.dto.response.ChatSessionResponse;
import com.devdocs.dto.response.SourceReferenceResponse;
import com.devdocs.entity.ChatMessage;
import com.devdocs.entity.ChatSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMapper {

	private static final TypeReference<List<SourceReferenceResponse>> SOURCE_LIST_TYPE = new TypeReference<>() {
	};

	private final ObjectMapper objectMapper;

	public ChatSessionResponse toSessionResponse(ChatSession session) {
		return ChatSessionResponse.builder()
				.id(session.getId())
				.title(session.getTitle())
				.messageCount(session.getMessageCount())
				.createdAt(session.getCreatedAt())
				.updatedAt(session.getUpdatedAt())
				.build();
	}

	public ChatMessageResponse toMessageResponse(ChatMessage message) {
		return ChatMessageResponse.builder()
				.id(message.getId())
				.sessionId(message.getSession().getId())
				.role(message.getRole())
				.content(message.getContent())
				.grounded(message.getGrounded())
				.sources(deserializeSources(message.getSourcesJson()))
				.createdAt(message.getCreatedAt())
				.build();
	}

	public String serializeSources(List<SourceReferenceResponse> sources) {
		if (sources == null || sources.isEmpty()) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(sources);
		} catch (JsonProcessingException ex) {
			log.warn("Failed to serialize source references: {}", ex.getMessage());
			return null;
		}
	}

	public List<SourceReferenceResponse> deserializeSources(String sourcesJson) {
		if (sourcesJson == null || sourcesJson.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(sourcesJson, SOURCE_LIST_TYPE);
		} catch (JsonProcessingException ex) {
			log.warn("Failed to deserialize source references: {}", ex.getMessage());
			return List.of();
		}
	}
}
