package com.devdocs.controller;

import com.devdocs.config.TestEmbeddingConfig;
import com.devdocs.dto.request.LoginRequest;
import com.devdocs.dto.request.RegisterRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestEmbeddingConfig.class)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void registerLoginAndAccessProtectedEndpoint() throws Exception {
		RegisterRequest registerRequest = new RegisterRequest("Alice Dev", "alice@devdocs.ai", "StrongPass123");

		MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(registerRequest)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token", notNullValue()))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.user.email").value("alice@devdocs.ai"))
				.andExpect(jsonPath("$.user.name").value("Alice Dev"))
				.andExpect(jsonPath("$.user.password").doesNotExist())
				.andReturn();

		JsonNode registerJson = objectMapper.readTree(registerResult.getResponse().getContentAsString());
		String token = registerJson.get("token").asText();

		// Protected /api/auth/me succeeds with Bearer token
		mockMvc.perform(get("/api/auth/me")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("alice@devdocs.ai"))
				.andExpect(jsonPath("$.name").value("Alice Dev"));

		// Login with valid credentials succeeds
		LoginRequest loginRequest = new LoginRequest("alice@devdocs.ai", "StrongPass123");
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(loginRequest)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token", notNullValue()))
				.andExpect(jsonPath("$.user.email").value("alice@devdocs.ai"));
	}

	@Test
	void duplicateEmailReturnsConflict() throws Exception {
		RegisterRequest first = new RegisterRequest("Bob Dev", "bob@devdocs.ai", "StrongPass123");
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(first)))
				.andExpect(status().isCreated());

		RegisterRequest duplicate = new RegisterRequest("Bob Two", "BOB@devdocs.ai", "AnotherPass123");
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(duplicate)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("An account with this email already exists"));
	}

	@Test
	void invalidRegistrationReturnsFieldErrors() throws Exception {
		RegisterRequest invalid = new RegisterRequest("", "not-an-email", "short");

		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(invalid)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.name").exists())
				.andExpect(jsonPath("$.fieldErrors.email").exists())
				.andExpect(jsonPath("$.fieldErrors.password").exists());
	}

	@Test
	void wrongPasswordReturnsUnauthorized() throws Exception {
		RegisterRequest registerRequest = new RegisterRequest("Carol Dev", "carol@devdocs.ai", "StrongPass123");
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(registerRequest)))
				.andExpect(status().isCreated());

		LoginRequest wrongPassword = new LoginRequest("carol@devdocs.ai", "WrongPass999");
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(wrongPassword)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void protectedEndpointWithoutTokenReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Authentication required"));
	}
}
