package com.devdocs.controller;

import com.devdocs.dto.request.AskQuestionRequest;
import com.devdocs.dto.request.CreateChatSessionRequest;
import com.devdocs.dto.response.ChatAnswerResponse;
import com.devdocs.dto.response.ChatMessageResponse;
import com.devdocs.dto.response.ChatSessionResponse;
import com.devdocs.dto.response.SendMessageResponse;
import com.devdocs.security.UserPrincipal;
import com.devdocs.service.ChatService;
import com.devdocs.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

	private final RagService ragService;
	private final ChatService chatService;

	@PostMapping("/ask")
	public ResponseEntity<ChatAnswerResponse> askQuestion(
			@Valid @RequestBody AskQuestionRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		return ResponseEntity.ok(ragService.ask(request.getQuestion(), principal.getId()));
	}

	@PostMapping("/sessions")
	public ResponseEntity<ChatSessionResponse> createSession(
			@Valid @RequestBody(required = false) CreateChatSessionRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		String title = request != null ? request.getTitle() : null;
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(chatService.createSession(principal.getId(), title));
	}

	@GetMapping("/sessions")
	public ResponseEntity<List<ChatSessionResponse>> listSessions(
			@AuthenticationPrincipal UserPrincipal principal) {
		return ResponseEntity.ok(chatService.listSessions(principal.getId()));
	}

	@GetMapping("/sessions/{id}/messages")
	public ResponseEntity<List<ChatMessageResponse>> getSessionMessages(
			@PathVariable UUID id,
			@AuthenticationPrincipal UserPrincipal principal) {
		return ResponseEntity.ok(chatService.getSessionMessages(id, principal.getId()));
	}

	@PostMapping("/sessions/{id}/messages")
	public ResponseEntity<SendMessageResponse> sendMessage(
			@PathVariable UUID id,
			@Valid @RequestBody AskQuestionRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(chatService.sendMessage(id, principal.getId(), request.getQuestion()));
	}

	@DeleteMapping("/sessions/{id}")
	public ResponseEntity<Void> deleteSession(
			@PathVariable UUID id,
			@AuthenticationPrincipal UserPrincipal principal) {
		chatService.deleteSession(id, principal.getId());
		return ResponseEntity.noContent().build();
	}
}
