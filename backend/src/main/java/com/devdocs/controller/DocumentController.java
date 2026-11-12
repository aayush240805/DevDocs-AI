package com.devdocs.controller;

import com.devdocs.dto.response.DocumentResponse;
import com.devdocs.security.UserPrincipal;
import com.devdocs.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

	private final DocumentService documentService;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<DocumentResponse> uploadDocument(
			@RequestParam("file") MultipartFile file,
			@RequestParam(value = "title", required = false) String title,
			@AuthenticationPrincipal UserPrincipal principal) {
		DocumentResponse response = documentService.uploadDocument(file, title, principal.getId());
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping
	public ResponseEntity<List<DocumentResponse>> listDocuments(@AuthenticationPrincipal UserPrincipal principal) {
		return ResponseEntity.ok(documentService.getUserDocuments(principal.getId()));
	}

	@GetMapping("/{id}")
	public ResponseEntity<DocumentResponse> getDocument(
			@PathVariable UUID id,
			@AuthenticationPrincipal UserPrincipal principal) {
		return ResponseEntity.ok(documentService.getUserDocument(id, principal.getId()));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteDocument(
			@PathVariable UUID id,
			@AuthenticationPrincipal UserPrincipal principal) {
		documentService.deleteDocument(id, principal.getId());
		return ResponseEntity.noContent().build();
	}
}
