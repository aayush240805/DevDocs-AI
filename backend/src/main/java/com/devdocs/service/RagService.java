package com.devdocs.service;

import com.devdocs.dto.response.ChatAnswerResponse;
import com.devdocs.dto.response.SourceReferenceResponse;
import com.devdocs.exception.AiServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

	private static final String INSUFFICIENT_INFO_MESSAGE =
			"I could not find sufficient information in your uploaded documents to answer this question. "
					+ "Please upload relevant documentation or try rephrasing your question.";

	private static final String SYSTEM_PROMPT = """
			You are DevDocs AI, a precise technical documentation assistant.
			Answer the user's question ONLY using the provided <context> excerpts from their uploaded documentation.
			Rules:
			1. Do NOT use outside general knowledge or fabricate APIs, commands, configurations, or document names.
			2. If the provided context does not contain enough information to answer the question, clearly state that the uploaded documents do not contain sufficient information.
			3. Cite the supporting sources inline using [Source N] matching the numbered context blocks.
			4. Keep the answer clear, accurate, and concise for software developers.
			""";

	private final VectorStore vectorStore;
	private final ChatClient chatClient;

	@Value("${app.rag.top-k:5}")
	private int topK;

	@Value("${app.rag.similarity-threshold:0.45}")
	private double similarityThreshold;

	public record ConversationTurn(String role, String content) {
	}

	public ChatAnswerResponse ask(String question, UUID userId) {
		return ask(question, userId, List.of());
	}

	public ChatAnswerResponse ask(String question, UUID userId, List<ConversationTurn> history) {
		String trimmedQuestion = question.trim();

		FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
		Filter.Expression userFilter = filterBuilder.eq("userId", userId.toString()).build();

		SearchRequest searchRequest = SearchRequest.builder()
				.query(trimmedQuestion)
				.topK(topK)
				.similarityThreshold(similarityThreshold)
				.filterExpression(userFilter)
				.build();

		List<Document> matches;
		try {
			matches = vectorStore.similaritySearch(searchRequest);
		} catch (Exception ex) {
			throw new AiServiceException("Failed to search your document embeddings", ex);
		}

		if (matches == null || matches.isEmpty()) {
			log.debug("No vector matches >= {} for user {}", similarityThreshold, userId);
			return ChatAnswerResponse.builder()
					.question(trimmedQuestion)
					.answer(INSUFFICIENT_INFO_MESSAGE)
					.grounded(false)
					.sources(List.of())
					.build();
		}

		String userPrompt = buildUserPrompt(trimmedQuestion, matches, history);
		String answer;
		try {
			answer = chatClient.prompt()
					.system(SYSTEM_PROMPT)
					.user(userPrompt)
					.call()
					.content();
		} catch (Exception ex) {
			throw new AiServiceException("Failed to generate answer from Gemini", ex);
		}

		if (answer == null || answer.isBlank()) {
			answer = INSUFFICIENT_INFO_MESSAGE;
		}

		List<SourceReferenceResponse> sources = extractSourceReferences(matches);
		return ChatAnswerResponse.builder()
				.question(trimmedQuestion)
				.answer(answer.trim())
				.grounded(true)
				.sources(sources)
				.build();
	}

	private String buildUserPrompt(
			String question,
			List<Document> matches,
			List<ConversationTurn> history) {
		StringBuilder sb = new StringBuilder();
		sb.append("<context>\n");
		for (int i = 0; i < matches.size(); i++) {
			Document doc = matches.get(i);
			Map<String, Object> meta = doc.getMetadata();
			String docName = String.valueOf(meta.getOrDefault("documentName", "Unknown Document"));
			Integer pageNumber = toInteger(meta.get("pageNumber"));
			Integer chunkIndex = toInteger(meta.get("chunkIndex"));

			sb.append("[Source ").append(i + 1)
					.append(" | Document: ").append(docName);
			if (pageNumber != null) {
				sb.append(" | Page: ").append(pageNumber);
			}
			if (chunkIndex != null) {
				sb.append(" | Chunk: ").append(chunkIndex);
			}
			sb.append("]\n");
			sb.append(doc.getText() == null ? "" : doc.getText().trim()).append("\n\n");
		}
		sb.append("</context>\n\n");

		if (history != null && !history.isEmpty()) {
			sb.append("<conversation_history>\n");
			for (ConversationTurn turn : history) {
				sb.append(turn.role()).append(": ").append(turn.content()).append("\n");
			}
			sb.append("</conversation_history>\n\n");
		}

		sb.append("<question>\n").append(question).append("\n</question>");
		return sb.toString();
	}

	private List<SourceReferenceResponse> extractSourceReferences(List<Document> matches) {
		List<SourceReferenceResponse> sources = new ArrayList<>();
		Set<String> seenKeys = new LinkedHashSet<>();

		for (Document doc : matches) {
			Map<String, Object> meta = doc.getMetadata();
			String documentId = meta.get("documentId") != null ? String.valueOf(meta.get("documentId")) : null;
			String documentName = String.valueOf(meta.getOrDefault("documentName", "Unknown Document"));
			Integer pageNumber = toInteger(meta.get("pageNumber"));
			Integer chunkIndex = toInteger(meta.get("chunkIndex"));

			String dedupKey = documentId + ":" + pageNumber + ":" + chunkIndex;
			if (!seenKeys.add(dedupKey)) {
				continue;
			}

			String text = doc.getText() == null ? "" : doc.getText().trim().replaceAll("\\s+", " ");
			String excerpt = text.length() > 220 ? text.substring(0, 217) + "..." : text;

			sources.add(SourceReferenceResponse.builder()
					.documentId(documentId)
					.documentName(documentName)
					.pageNumber(pageNumber)
					.chunkIndex(chunkIndex)
					.score(doc.getScore())
					.excerpt(excerpt)
					.build());
		}
		return sources;
	}

	private Integer toInteger(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value instanceof String str) {
			try {
				return Integer.parseInt(str);
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
	}
}
