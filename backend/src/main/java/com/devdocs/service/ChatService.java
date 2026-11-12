package com.devdocs.service;

import com.devdocs.dto.response.ChatAnswerResponse;
import com.devdocs.dto.response.ChatMessageResponse;
import com.devdocs.dto.response.ChatSessionResponse;
import com.devdocs.dto.response.SendMessageResponse;
import com.devdocs.entity.ChatMessage;
import com.devdocs.entity.ChatSession;
import com.devdocs.entity.User;
import com.devdocs.enums.MessageRole;
import com.devdocs.exception.ResourceNotFoundException;
import com.devdocs.mapper.ChatMapper;
import com.devdocs.repository.ChatMessageRepository;
import com.devdocs.repository.ChatSessionRepository;
import com.devdocs.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

	private static final String DEFAULT_SESSION_TITLE = "New Chat";
	private static final int MAX_HISTORY_MESSAGES = 6;
	private static final int AUTO_TITLE_MAX_LENGTH = 60;

	private final ChatSessionRepository chatSessionRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final UserRepository userRepository;
	private final RagService ragService;
	private final ChatMapper chatMapper;

	@Transactional
	public ChatSessionResponse createSession(UUID userId, String requestedTitle) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));

		String title = (requestedTitle == null || requestedTitle.isBlank())
				? DEFAULT_SESSION_TITLE
				: requestedTitle.trim();

		ChatSession session = ChatSession.builder()
				.title(title)
				.messageCount(0)
				.user(user)
				.build();

		return chatMapper.toSessionResponse(chatSessionRepository.save(session));
	}

	@Transactional(readOnly = true)
	public List<ChatSessionResponse> listSessions(UUID userId) {
		return chatSessionRepository.findAllByUserIdOrderByUpdatedAtDesc(userId).stream()
				.map(chatMapper::toSessionResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<ChatMessageResponse> getSessionMessages(UUID sessionId, UUID userId) {
		findOwnedSession(sessionId, userId);
		return chatMessageRepository.findAllBySessionIdOrderBySequenceNumberAsc(sessionId).stream()
				.map(chatMapper::toMessageResponse)
				.toList();
	}

	@Transactional
	public SendMessageResponse sendMessage(UUID sessionId, UUID userId, String question) {
		ChatSession session = findOwnedSession(sessionId, userId);
		String trimmedQuestion = question.trim();

		List<ChatMessage> existingMessages =
				chatMessageRepository.findAllBySessionIdOrderBySequenceNumberAsc(sessionId);

		int startIndex = Math.max(0, existingMessages.size() - MAX_HISTORY_MESSAGES);
		List<RagService.ConversationTurn> recentHistory = existingMessages
				.subList(startIndex, existingMessages.size())
				.stream()
				.map(msg -> new RagService.ConversationTurn(msg.getRole().name(), msg.getContent()))
				.toList();

		ChatAnswerResponse ragResponse = ragService.ask(trimmedQuestion, userId, recentHistory);

		int nextSeq = existingMessages.isEmpty()
				? 1
				: existingMessages.get(existingMessages.size() - 1).getSequenceNumber() + 1;

		ChatMessage userMessage = ChatMessage.builder()
				.session(session)
				.role(MessageRole.USER)
				.content(trimmedQuestion)
				.sequenceNumber(nextSeq)
				.grounded(null)
				.sourcesJson(null)
				.build();

		ChatMessage assistantMessage = ChatMessage.builder()
				.session(session)
				.role(MessageRole.ASSISTANT)
				.content(ragResponse.getAnswer())
				.sequenceNumber(nextSeq + 1)
				.grounded(ragResponse.isGrounded())
				.sourcesJson(chatMapper.serializeSources(ragResponse.getSources()))
				.build();

		ChatMessage savedUserMsg = chatMessageRepository.save(userMessage);
		ChatMessage savedAssistantMsg = chatMessageRepository.save(assistantMessage);

		if (session.getMessageCount() == 0 && DEFAULT_SESSION_TITLE.equals(session.getTitle())) {
			session.setTitle(buildAutoTitle(trimmedQuestion));
		}
		session.setMessageCount(session.getMessageCount() + 2);
		ChatSession updatedSession = chatSessionRepository.saveAndFlush(session);

		return SendMessageResponse.builder()
				.session(chatMapper.toSessionResponse(updatedSession))
				.userMessage(chatMapper.toMessageResponse(savedUserMsg))
				.assistantMessage(chatMapper.toMessageResponse(savedAssistantMsg))
				.build();
	}

	@Transactional
	public void deleteSession(UUID sessionId, UUID userId) {
		ChatSession session = findOwnedSession(sessionId, userId);
		chatMessageRepository.deleteAllBySessionId(session.getId());
		chatSessionRepository.delete(session);
		chatSessionRepository.flush();
	}

	private ChatSession findOwnedSession(UUID sessionId, UUID userId) {
		return chatSessionRepository.findByIdAndUserId(sessionId, userId)
				.orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
	}

	private String buildAutoTitle(String question) {
		String normalized = question.replaceAll("\\s+", " ").trim();
		if (normalized.length() <= AUTO_TITLE_MAX_LENGTH) {
			return normalized;
		}
		return normalized.substring(0, AUTO_TITLE_MAX_LENGTH - 3) + "...";
	}
}
