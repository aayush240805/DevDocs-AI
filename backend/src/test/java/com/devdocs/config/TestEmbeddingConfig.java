package com.devdocs.config;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

@TestConfiguration
public class TestEmbeddingConfig {

	private static final int DIMENSIONS = 768;

	@Bean
	@Primary
	public ChatModel testChatModel() {
		return new ChatModel() {
			@Override
			public ChatResponse call(Prompt prompt) {
				String content = prompt.getContents();
				String reply = content.contains("HikariCP")
						? "Based on your documentation [Source 1], HikariCP is used for JDBC connection pooling."
						: "Based on the provided context [Source 1], here is the relevant detail from your documentation.";
				return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
			}
		};
	}

	@Bean
	@Primary
	public EmbeddingModel testEmbeddingModel() {
		return new EmbeddingModel() {
			@Override
			public EmbeddingResponse call(EmbeddingRequest request) {
				List<String> inputs = request.getInstructions();
				List<Embedding> embeddings = new ArrayList<>(inputs.size());
				for (int i = 0; i < inputs.size(); i++) {
					embeddings.add(new Embedding(deterministicVector(inputs.get(i)), i));
				}
				return new EmbeddingResponse(embeddings);
			}

			@Override
			public float[] embed(Document document) {
				return deterministicVector(document.getText());
			}

			@Override
			public int dimensions() {
				return DIMENSIONS;
			}
		};
	}

	private static float[] deterministicVector(String text) {
		float[] vec = new float[DIMENSIONS];
		if (text == null || text.isBlank()) {
			vec[0] = 1.0f;
			return vec;
		}
		// Simple bag-of-words hashed projection so related texts have higher cosine similarity
		String[] tokens = text.toLowerCase().split("\\W+");
		for (String token : tokens) {
			if (token.isEmpty()) continue;
			int bucket = Math.floorMod(token.hashCode(), DIMENSIONS);
			vec[bucket] += 1.0f;
		}
		float norm = 0.0f;
		for (float v : vec) {
			norm += v * v;
		}
		norm = (float) Math.sqrt(norm);
		if (norm == 0.0f) {
			vec[0] = 1.0f;
		} else {
			for (int i = 0; i < vec.length; i++) {
				vec[i] /= norm;
			}
		}
		return vec;
	}
}
