package com.devdocs.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

	private final SecretKey signingKey;
	private final long expirationMs;

	public JwtService(
			@Value("${app.jwt.secret}") String base64Secret,
			@Value("${app.jwt.expiration-ms}") long expirationMs) {
		byte[] keyBytes = Decoders.BASE64.decode(base64Secret);
		this.signingKey = Keys.hmacShaKeyFor(keyBytes);
		this.expirationMs = expirationMs;
	}

	public String generateToken(UserPrincipal principal) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + expirationMs);
		return Jwts.builder()
				.subject(principal.getEmail())
				.claim("userId", principal.getId().toString())
				.issuedAt(now)
				.expiration(expiry)
				.signWith(signingKey)
				.compact();
	}

	public String extractEmail(String token) {
		return parseClaims(token).getSubject();
	}

	public UUID extractUserId(String token) {
		String userId = parseClaims(token).get("userId", String.class);
		return UUID.fromString(userId);
	}

	public boolean isTokenValid(String token, UserDetails userDetails) {
		try {
			Claims claims = parseClaims(token);
			String email = claims.getSubject();
			Date expiration = claims.getExpiration();
			return email != null
					&& email.equals(userDetails.getUsername())
					&& expiration != null
					&& expiration.after(new Date());
		} catch (JwtException | IllegalArgumentException ex) {
			return false;
		}
	}

	public long getExpirationMs() {
		return expirationMs;
	}

	private Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(signingKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
}
