package com.devdocs.service;

import com.devdocs.entity.Document;
import com.devdocs.entity.User;
import com.devdocs.enums.DocumentStatus;
import com.devdocs.enums.FileType;
import com.devdocs.exception.AiServiceException;
import com.devdocs.exception.BadRequestException;
import com.devdocs.mapper.DocumentMapper;
import com.devdocs.repository.DocumentRepository;
import com.devdocs.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

	@Mock
	private DocumentRepository documentRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private DocumentProcessingService documentProcessingService;

	@Mock
	private DocumentMapper documentMapper;

	@InjectMocks
	private DocumentService documentService;

	@Test
	void marksDocumentFailedWhenEmbeddingStorageFails() {
		ReflectionTestUtils.setField(documentService, "maxSizeBytes", 10_485_760L);

		UUID userId = UUID.randomUUID();
		User user = User.builder().name("Alice").email("alice@devdocs.ai").password("hash").build();
		user.setId(userId);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
			Document doc = invocation.getArgument(0);
			if (doc.getId() == null) {
				doc.setId(UUID.randomUUID());
			}
			return doc;
		});
		when(documentProcessingService.processAndStore(any(), any(FileType.class), any(), any(), any(), any()))
				.thenThrow(new AiServiceException("Gemini API quota exceeded", new RuntimeException("429")));

		MockMultipartFile file = new MockMultipartFile(
				"file",
				"guide.txt",
				"text/plain",
				"Some technical documentation text long enough to process.".getBytes(StandardCharsets.UTF_8)
		);

		assertThatThrownBy(() -> documentService.uploadDocument(file, "Guide", userId))
				.isInstanceOf(AiServiceException.class)
				.hasMessageContaining("Gemini API quota exceeded");

		ArgumentCaptor<Document> savedCaptor = ArgumentCaptor.forClass(Document.class);
		verify(documentRepository, atLeast(2)).save(savedCaptor.capture());

		Document finalSaved = savedCaptor.getValue();
		assertThat(finalSaved.getProcessingStatus()).isEqualTo(DocumentStatus.FAILED);
		assertThat(finalSaved.getErrorMessage()).contains("Gemini API quota exceeded");
		verify(documentProcessingService).deleteDocumentVectors(finalSaved.getId(), userId);
	}

	@Test
	void rejectsOversizedFileBeforeProcessing() {
		ReflectionTestUtils.setField(documentService, "maxSizeBytes", 1024L);
		UUID userId = UUID.randomUUID();

		MockMultipartFile oversized = new MockMultipartFile(
				"file",
				"huge.txt",
				"text/plain",
				new byte[2048]
		);

		assertThatThrownBy(() -> documentService.uploadDocument(oversized, "Huge File", userId))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("maximum allowed size");

		verifyNoInteractions(documentProcessingService, documentRepository);
	}
}
