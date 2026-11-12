package com.devdocs.security;

import com.devdocs.config.TestEmbeddingConfig;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestEmbeddingConfig.class)
class SecurityAndEdgeCaseTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void rejectsTamperedMalformedAndMissingBearerTokens() throws Exception {
		String validToken = registerAndGetToken("Sec User", "sec.user@devdocs.ai");
		String tamperedToken = validToken.substring(0, validToken.length() - 4) + "zzzz";

		mockMvc.perform(get("/api/documents")
						.header("Authorization", "Bearer " + tamperedToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Authentication required"));

		mockMvc.perform(get("/api/chat/sessions")
						.header("Authorization", "Bearer not-even-a-jwt"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/auth/me")
						.header("Authorization", "Basic dXNlcjpwYXNz"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void handlesInvalidUuidMalformedJsonAndMissingMultipartSafely() throws Exception {
		String token = registerAndGetToken("Edge Tester", "edge.tester@devdocs.ai");

		mockMvc.perform(get("/api/documents/not-a-valid-uuid")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));

		mockMvc.perform(delete("/api/chat/sessions/bad-uuid-value")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/chat/ask")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\": "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Malformed JSON request body"));

		mockMvc.perform(multipart("/api/documents")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsSpoofedMimeCorruptedPdfAndUnindexableTextAndMarksFailed() throws Exception {
		String token = registerAndGetToken("File Guard", "file.guard@devdocs.ai");

		// 1. MIME spoofing (.pdf filename with image/png Content-Type)
		MockMultipartFile spoofedPdf = new MockMultipartFile(
				"file",
				"spoofed.pdf",
				MediaType.IMAGE_PNG_VALUE,
				"fake-pdf".getBytes(StandardCharsets.UTF_8)
		);
		mockMvc.perform(multipart("/api/documents")
						.file(spoofedPdf)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid MIME type for PDF file"));

		// 2. Corrupted PDF bytes -> returns 400 and records FAILED document without leaking stack traces
		MockMultipartFile corruptedPdf = new MockMultipartFile(
				"file",
				"corrupt.pdf",
				MediaType.APPLICATION_PDF_VALUE,
				"definitely-not-a-valid-pdf-stream".getBytes(StandardCharsets.UTF_8)
		);
		mockMvc.perform(multipart("/api/documents")
						.file(corruptedPdf)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Failed to read PDF file. Ensure it is a valid, uncorrupted PDF."));

		// 3. Too-short TXT (<20 chars produces 0 chunks) -> marks FAILED
		MockMultipartFile shortTxt = new MockMultipartFile(
				"file",
				"tiny.txt",
				MediaType.TEXT_PLAIN_VALUE,
				"Too short".getBytes(StandardCharsets.UTF_8)
		);
		mockMvc.perform(multipart("/api/documents")
						.file(shortTxt)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/documents")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].processingStatus").value("FAILED"))
				.andExpect(jsonPath("$[1].processingStatus").value("FAILED"));
	}

	@Test
	void enforcesCorsAllowedOriginsOnPreflight() throws Exception {
		mockMvc.perform(options("/api/documents")
						.header("Origin", "http://localhost:5173")
						.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

		mockMvc.perform(options("/api/documents")
						.header("Origin", "https://malicious.example.com")
						.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isForbidden());
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
