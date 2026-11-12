package com.devdocs.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateChatSessionRequest {

	@Size(max = 255, message = "Title must not exceed 255 characters")
	private String title;
}
