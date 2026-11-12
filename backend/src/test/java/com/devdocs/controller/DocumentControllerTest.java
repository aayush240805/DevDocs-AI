package com.devdocs.controller;

import com.devdocs.config.TestEmbeddingConfig;
import com.devdocs.dto.request.RegisterRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
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
class DocumentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private VectorStore vectorStore;

	@Test
	void uploadListGetAndDeletePdfDocxAndTxt() throws Exception {
		String token = registerAndGetToken("Doc Owner", "owner@devdocs.ai");

		// 1. Upload TXT (must be >= 20 chars for TokenTextSplitter minChunkLengthToEmbed)
		MockMultipartFile txtFile = new MockMultipartFile(
				"file",
				"spring-notes.txt",
				MediaType.TEXT_PLAIN_VALUE,
				"Spring Boot uses HikariCP for high-performance JDBC connection pooling out of the box.".getBytes(StandardCharsets.UTF_8)
		);
		mockMvc.perform(multipart("/api/documents")
						.file(txtFile)
						.param("title", "Spring Pool Notes")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Spring Pool Notes"))
				.andExpect(jsonPath("$.fileType").value("TXT"))
				.andExpect(jsonPath("$.processingStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.pageCount").value(1))
				.andExpect(jsonPath("$.chunkCount", greaterThan(0)));

		// 2. Upload 2-page PDF
		byte[] pdfBytes = createSamplePdfBytes(
				"Page one technical docs covering PostgreSQL indexing and pgvector storage.",
				"Page two deployment guide covering Docker Compose and environment variables."
		);
		MockMultipartFile pdfFile = new MockMultipartFile(
				"file",
				"architecture.pdf",
				MediaType.APPLICATION_PDF_VALUE,
				pdfBytes
		);
		MvcResult pdfResult = mockMvc.perform(multipart("/api/documents")
						.file(pdfFile)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("architecture"))
				.andExpect(jsonPath("$.fileType").value("PDF"))
				.andExpect(jsonPath("$.processingStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.pageCount").value(2))
				.andExpect(jsonPath("$.chunkCount", greaterThan(0)))
				.andReturn();

		String pdfDocId = objectMapper.readTree(pdfResult.getResponse().getContentAsString()).get("id").asText();

		Integer storedPdfChunks = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM vector_store WHERE metadata->>'documentId' = ?",
				Integer.class,
				pdfDocId
		);
		assertThat(storedPdfChunks).isNotNull().isGreaterThan(0);

		// 3. Upload DOCX
		byte[] docxBytes = createSampleDocxBytes("Apache POI extracts text from Word documents for semantic indexing.");
		MockMultipartFile docxFile = new MockMultipartFile(
				"file",
				"guide.docx",
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
				docxBytes
		);
		mockMvc.perform(multipart("/api/documents")
						.file(docxFile)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fileType").value("DOCX"))
				.andExpect(jsonPath("$.processingStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.pageCount").value(1))
				.andExpect(jsonPath("$.chunkCount", greaterThan(0)));

		// 4. List documents returns all 3
		mockMvc.perform(get("/api/documents")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)));

		// 5. Get single document
		mockMvc.perform(get("/api/documents/" + pdfDocId)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(pdfDocId));

		// 6. Delete document -> removes both DB row and pgvector chunks
		mockMvc.perform(delete("/api/documents/" + pdfDocId)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isNoContent());

		Integer remainingPdfChunks = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM vector_store WHERE metadata->>'documentId' = ?",
				Integer.class,
				pdfDocId
		);
		assertThat(remainingPdfChunks).isZero();

		mockMvc.perform(get("/api/documents/" + pdfDocId)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void enforcesStrictUserIsolation() throws Exception {
		String userAToken = registerAndGetToken("User A", "usera@devdocs.ai");
		String userBToken = registerAndGetToken("User B", "userb@devdocs.ai");

		MockMultipartFile file = new MockMultipartFile(
				"file",
				"secret-a.txt",
				MediaType.TEXT_PLAIN_VALUE,
				"Confidential architecture spec for User A".getBytes(StandardCharsets.UTF_8)
		);
		MvcResult result = mockMvc.perform(multipart("/api/documents")
						.file(file)
						.header("Authorization", "Bearer " + userAToken))
				.andExpect(status().isCreated())
				.andReturn();

		String docId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

		// User B list is empty
		mockMvc.perform(get("/api/documents")
						.header("Authorization", "Bearer " + userBToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));

		// User B cannot get or delete User A's document
		mockMvc.perform(get("/api/documents/" + docId)
						.header("Authorization", "Bearer " + userBToken))
				.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/documents/" + docId)
						.header("Authorization", "Bearer " + userBToken))
				.andExpect(status().isNotFound());

		// Vector search filtered by User A finds the chunk; filtered by User B finds nothing
		String userAId = jdbcTemplate.queryForObject(
				"SELECT id::text FROM users WHERE email = ?", String.class, "usera@devdocs.ai");
		String userBId = jdbcTemplate.queryForObject(
				"SELECT id::text FROM users WHERE email = ?", String.class, "userb@devdocs.ai");

		List<Document> userAHits = vectorStore.similaritySearch(
				SearchRequest.builder()
						.query("Confidential architecture spec")
						.topK(5)
						.filterExpression("userId == '" + userAId + "'")
						.build()
		);
		assertThat(userAHits).isNotEmpty();
		assertThat(userAHits.get(0).getMetadata())
				.containsEntry("documentId", docId)
				.containsEntry("userId", userAId)
				.containsEntry("documentName", "secret-a");

		List<Document> userBHits = vectorStore.similaritySearch(
				SearchRequest.builder()
						.query("Confidential architecture spec")
						.topK(5)
						.filterExpression("userId == '" + userBId + "'")
						.build()
		);
		assertThat(userBHits).isEmpty();
	}

	@Test
	void rejectsInvalidAndEmptyFiles() throws Exception {
		String token = registerAndGetToken("Validator", "validator@devdocs.ai");

		MockMultipartFile invalidExt = new MockMultipartFile(
				"file",
				"diagram.png",
				MediaType.IMAGE_PNG_VALUE,
				"fake-png".getBytes(StandardCharsets.UTF_8)
		);
		mockMvc.perform(multipart("/api/documents")
						.file(invalidExt)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Unsupported file type. Allowed formats: PDF, DOCX, TXT"));

		MockMultipartFile emptyFile = new MockMultipartFile(
				"file",
				"empty.txt",
				MediaType.TEXT_PLAIN_VALUE,
				new byte[0]
		);
		mockMvc.perform(multipart("/api/documents")
						.file(emptyFile)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/documents"))
				.andExpect(status().isUnauthorized());
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

	private byte[] createSamplePdfBytes(String... pageTexts) throws Exception {
		try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
			for (String text : pageTexts) {
				PDPage page = new PDPage();
				doc.addPage(page);
				try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
					stream.beginText();
					stream.setFont(font, 12);
					stream.newLineAtOffset(50, 700);
					stream.showText(text);
					stream.endText();
				}
			}
			doc.save(out);
			return out.toByteArray();
		}
	}

	private byte[] createSampleDocxBytes(String text) throws Exception {
		try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			doc.createParagraph().createRun().setText(text);
			doc.write(out);
			return out.toByteArray();
		}
	}
}
