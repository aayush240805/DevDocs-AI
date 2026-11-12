package com.devdocs.controller;

import com.devdocs.config.TestEmbeddingConfig;
import com.devdocs.dto.request.AskQuestionRequest;
import com.devdocs.dto.request.RegisterRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestEmbeddingConfig.class)
class ChatControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void answersQuestionWithGroundedSourceReferencesAndIsolatesUsers() throws Exception {
		String userAToken = registerAndGetToken("Alice RAG", "alice.rag@devdocs.ai");
		String userBToken = registerAndGetToken("Bob RAG", "bob.rag@devdocs.ai");

		MockMultipartFile userADoc = new MockMultipartFile(
				"file",
				"hikari-guide.txt",
				MediaType.TEXT_PLAIN_VALUE,
				"Spring Boot configures HikariCP connection pooling with maximum-pool-size and connection-timeout settings."
						.getBytes(StandardCharsets.UTF_8)
		);
		MvcResult uploadRes = mockMvc.perform(multipart("/api/documents")
						.file(userADoc)
						.param("title", "HikariCP Pooling Guide")
						.header("Authorization", "Bearer " + userAToken))
				.andExpect(status().isCreated())
				.andReturn();
		String userADocId = objectMapper.readTree(uploadRes.getResponse().getContentAsString()).get("id").asText();

		AskQuestionRequest question = new AskQuestionRequest("How does Spring Boot configure HikariCP connection pooling?");
		mockMvc.perform(post("/api/chat/ask")
						.header("Authorization", "Bearer " + userAToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(question)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.grounded").value(true))
				.andExpect(jsonPath("$.answer", containsString("HikariCP")))
				.andExpect(jsonPath("$.sources", hasSize(1)))
				.andExpect(jsonPath("$.sources[0].documentId").value(userADocId))
				.andExpect(jsonPath("$.sources[0].documentName").value("HikariCP Pooling Guide"))
				.andExpect(jsonPath("$.sources[0].pageNumber").value(1))
				.andExpect(jsonPath("$.sources[0].chunkIndex").value(0));

		mockMvc.perform(post("/api/chat/ask")
						.header("Authorization", "Bearer " + userBToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(question)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.grounded").value(false))
				.andExpect(jsonPath("$.answer", containsString("could not find sufficient information")))
				.andExpect(jsonPath("$.sources", hasSize(0)));
	}

	@Test
	void rejectsBlankQuestionAndUnauthenticatedRequest() throws Exception {
		String token = registerAndGetToken("Validator RAG", "validator.rag@devdocs.ai");

		mockMvc.perform(post("/api/chat/ask")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.question").exists());

		mockMvc.perform(post("/api/chat/ask")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"What is HikariCP?\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void managesChatSessionsHistoryCitationsAndEnforcesOwnership() throws Exception {
		String aliceToken = registerAndGetToken("Alice Session", "alice.session@devdocs.ai");
		String bobToken = registerAndGetToken("Bob Session", "bob.session@devdocs.ai");

		MockMultipartFile aliceDoc = new MockMultipartFile(
				"file",
				"hikari-notes.txt",
				MediaType.TEXT_PLAIN_VALUE,
				"Spring Boot configures HikariCP connection pooling automatically using DataSourceProperties."
						.getBytes(StandardCharsets.UTF_8)
		);
		mockMvc.perform(multipart("/api/documents")
						.file(aliceDoc)
						.param("title", "HikariCP Notes")
						.header("Authorization", "Bearer " + aliceToken))
				.andExpect(status().isCreated());

		// 1. Alice creates a chat session without custom title -> defaults to "New Chat"
		MvcResult sessionRes = mockMvc.perform(post("/api/chat/sessions")
						.header("Authorization", "Bearer " + aliceToken))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("New Chat"))
				.andExpect(jsonPath("$.messageCount").value(0))
				.andReturn();
		String sessionId = objectMapper.readTree(sessionRes.getResponse().getContentAsString()).get("id").asText();

		// 2. Alice sends first question -> auto-titles session, saves USER + ASSISTANT messages + sourcesJson
		AskQuestionRequest firstQuestion = new AskQuestionRequest("How does Spring Boot configure HikariCP connection pooling?");
		mockMvc.perform(post("/api/chat/sessions/" + sessionId + "/messages")
						.header("Authorization", "Bearer " + aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(firstQuestion)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.session.title").value("How does Spring Boot configure HikariCP connection pooling?"))
				.andExpect(jsonPath("$.session.messageCount").value(2))
				.andExpect(jsonPath("$.userMessage.role").value("USER"))
				.andExpect(jsonPath("$.assistantMessage.role").value("ASSISTANT"))
				.andExpect(jsonPath("$.assistantMessage.grounded").value(true))
				.andExpect(jsonPath("$.assistantMessage.sources", hasSize(1)))
				.andExpect(jsonPath("$.assistantMessage.sources[0].documentName").value("HikariCP Notes"));

		// 3. Alice sends follow-up message -> session reaches 4 messages
		AskQuestionRequest followUp = new AskQuestionRequest("Which settings control HikariCP connection pooling?");
		mockMvc.perform(post("/api/chat/sessions/" + sessionId + "/messages")
						.header("Authorization", "Bearer " + aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(followUp)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.session.messageCount").value(4));

		// 4. Retrieve full ordered history with persisted sources
		mockMvc.perform(get("/api/chat/sessions/" + sessionId + "/messages")
						.header("Authorization", "Bearer " + aliceToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(4)))
				.andExpect(jsonPath("$[0].role").value("USER"))
				.andExpect(jsonPath("$[1].role").value("ASSISTANT"))
				.andExpect(jsonPath("$[1].sources", hasSize(1)))
				.andExpect(jsonPath("$[2].role").value("USER"))
				.andExpect(jsonPath("$[3].role").value("ASSISTANT"));

		// 5. Bob cannot list, read, message, or delete Alice's session (IDOR protection)
		mockMvc.perform(get("/api/chat/sessions")
						.header("Authorization", "Bearer " + bobToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));

		mockMvc.perform(get("/api/chat/sessions/" + sessionId + "/messages")
						.header("Authorization", "Bearer " + bobToken))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/chat/sessions/" + sessionId + "/messages")
						.header("Authorization", "Bearer " + bobToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(followUp)))
				.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/chat/sessions/" + sessionId)
						.header("Authorization", "Bearer " + bobToken))
				.andExpect(status().isNotFound());

		// 6. Alice deletes her session -> returns 204 and subsequent lookup returns 404
		mockMvc.perform(delete("/api/chat/sessions/" + sessionId)
						.header("Authorization", "Bearer " + aliceToken))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/chat/sessions/" + sessionId + "/messages")
						.header("Authorization", "Bearer " + aliceToken))
				.andExpect(status().isNotFound());
	}

	private String registerAndGetToken(String name, String email) throws Exception {
		RegisterRequest request = new RegisterRequest(name, email, "StrongPass123");
		MvcResult res = mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn();
		JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
		return json.get("token").asText();
	}
}
