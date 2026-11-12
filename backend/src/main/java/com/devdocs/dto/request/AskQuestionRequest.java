package com.devdocs.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AskQuestionRequest {

	@NotBlank(message = "Question is required")
	@Size(max = 2000, message = "Question must not exceed 2000 characters")
	private String question;
}
