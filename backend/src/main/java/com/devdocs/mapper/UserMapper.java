package com.devdocs.mapper;

import com.devdocs.dto.response.UserResponse;
import com.devdocs.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

	public UserResponse toResponse(User user) {
		return UserResponse.builder()
				.id(user.getId())
				.name(user.getName())
				.email(user.getEmail())
				.createdAt(user.getCreatedAt())
				.build();
	}
}
