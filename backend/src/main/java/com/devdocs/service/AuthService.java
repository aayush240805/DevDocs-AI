package com.devdocs.service;

import com.devdocs.dto.request.LoginRequest;
import com.devdocs.dto.request.RegisterRequest;
import com.devdocs.dto.response.AuthResponse;
import com.devdocs.dto.response.UserResponse;
import com.devdocs.entity.User;
import com.devdocs.exception.ConflictException;
import com.devdocs.exception.ResourceNotFoundException;
import com.devdocs.mapper.UserMapper;
import com.devdocs.repository.UserRepository;
import com.devdocs.security.JwtService;
import com.devdocs.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuthenticationManager authenticationManager;
	private final UserMapper userMapper;

	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String normalizedEmail = request.getEmail().trim().toLowerCase();
		if (userRepository.existsByEmail(normalizedEmail)) {
			throw new ConflictException("An account with this email already exists");
		}

		User user = User.builder()
				.name(request.getName().trim())
				.email(normalizedEmail)
				.password(passwordEncoder.encode(request.getPassword()))
				.build();

		User savedUser = userRepository.save(user);
		UserPrincipal principal = new UserPrincipal(savedUser);
		String token = jwtService.generateToken(principal);

		return AuthResponse.builder()
				.token(token)
				.tokenType("Bearer")
				.expiresInMs(jwtService.getExpirationMs())
				.user(userMapper.toResponse(savedUser))
				.build();
	}

	public AuthResponse login(LoginRequest request) {
		String normalizedEmail = request.getEmail().trim().toLowerCase();
		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
		);

		UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
		User user = userRepository.findById(principal.getId())
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
		String token = jwtService.generateToken(principal);

		return AuthResponse.builder()
				.token(token)
				.tokenType("Bearer")
				.expiresInMs(jwtService.getExpirationMs())
				.user(userMapper.toResponse(user))
				.build();
	}

	@Transactional(readOnly = true)
	public UserResponse getCurrentUser(UUID userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
		return userMapper.toResponse(user);
	}
}
