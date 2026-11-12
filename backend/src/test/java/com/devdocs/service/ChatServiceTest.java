package com.devdocs.service;

import com.devdocs.dto.response.ChatAnswerResponse;
import com.devdocs.dto.response.SendMessageResponse;
import com.devdocs.entity.ChatMessage;
import com.devdocs.entity.ChatSession;
import com.devdocs.entity.User;
import com.devdocs.enums.MessageRole;
import com.devdocs.mapper.ChatMapper;
import com.devdocs.repository.ChatMessageRepository;
import com.devdocs.repository.ChatSessionRepository;
import com.devdocs.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

	@Mock
	private ChatSessionRepository chatSessionRepository;

	@Mock
	private ChatMessageRepository chatMessageRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private RagService ragService;

	private ChatService chatService;

	@BeforeEach
	void setUp() {
		chatService = new ChatService(
				chatSessionRepository,
				chatMessageRepository,
				userRepository,
				ragService,
				new ChatMapper(new ObjectMapper())
		);
	}

	@Test
	void passesPriorConversationTurnsToRagServiceAndPersistsExchange() {
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();

		User user = User.builder().name("Alice").email("alice@devdocs.ai").password("hash").build();
		user.setId(userId);

		ChatSession session = ChatSession.builder()
				.title("Spring Security")
				.messageCount(2)
				.user(user)
				.build();
		session.setId(sessionId);

		ChatMessage prevUser = ChatMessage.builder()
				.session(session)
				.role(MessageRole.USER)
				.content("What filter validates JWT?")
				.sequenceNumber(1)
				.build();
		ChatMessage prevAssistant = ChatMessage.builder()
				.session(session)
				.role(MessageRole.ASSISTANT)
				.content("JwtAuthenticationFilter validates the Bearer token.")
				.sequenceNumber(2)
				.grounded(true)
				.build();

		when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
		when(chatMessageRepository.findAllBySessionIdOrderBySequenceNumberAsc(sessionId))
				.thenReturn(List.of(prevUser, prevAssistant));
		when(ragService.ask(eq("Where is it registered?"), eq(userId), any()))
				.thenReturn(ChatAnswerResponse.builder()
						.question("Where is it registered?")
						.answer("It is registered in SecurityConfig before UsernamePasswordAuthenticationFilter.")
						.grounded(true)
						.sources(List.of())
						.build());
		when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
		when(chatSessionRepository.saveAndFlush(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

		SendMessageResponse response = chatService.sendMessage(sessionId, userId, "Where is it registered?");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<RagService.ConversationTurn>> historyCaptor = ArgumentCaptor.forClass(List.class);
		verify(ragService).ask(eq("Where is it registered?"), eq(userId), historyCaptor.capture());

		List<RagService.ConversationTurn> passedHistory = historyCaptor.getValue();
		assertThat(passedHistory).hasSize(2);
		assertThat(passedHistory.get(0).role()).isEqualTo("USER");
		assertThat(passedHistory.get(1).role()).isEqualTo("ASSISTANT");

		assertThat(response.getSession().getMessageCount()).isEqualTo(4);
		assertThat(response.getSession().getTitle()).isEqualTo("Spring Security");
		assertThat(response.getAssistantMessage().getGrounded()).isTrue();
	}
}
