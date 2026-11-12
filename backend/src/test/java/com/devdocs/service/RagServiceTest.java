package com.devdocs.service;

import com.devdocs.dto.response.ChatAnswerResponse;
import com.devdocs.exception.AiServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

	@Mock
	private VectorStore vectorStore;

	@Mock
	private ChatClient chatClient;

	private RagService ragService;

	@BeforeEach
	void setUp() {
		ragService = new RagService(vectorStore, chatClient);
		ReflectionTestUtils.setField(ragService, "topK", 5);
		ReflectionTestUtils.setField(ragService, "similarityThreshold", 0.45);
	}

	@Test
	void doesNotCallGeminiWhenNoRelevantChunksFound() {
		UUID userId = UUID.randomUUID();
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		ChatAnswerResponse response = ragService.ask("What is Kubernetes?", userId);

		assertThat(response.isGrounded()).isFalse();
		assertThat(response.getSources()).isEmpty();
		assertThat(response.getAnswer()).contains("could not find sufficient information");
		verifyNoInteractions(chatClient);
	}

	@Test
	void wrapsVectorSearchFailuresInAiServiceException() {
		UUID userId = UUID.randomUUID();
		when(vectorStore.similaritySearch(any(SearchRequest.class)))
				.thenThrow(new RuntimeException("Embedding API timeout"));

		assertThatThrownBy(() -> ragService.ask("How do I configure SSL?", userId))
				.isInstanceOf(AiServiceException.class)
				.hasMessageContaining("Failed to search your document embeddings");
	}
}
