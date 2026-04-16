package com.family.finance.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiry-ms}") long accessTokenExpiryMs,
            @Value("${app.jwt.refresh-token-expiry-ms}") long refreshTokenExpiryMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    public String generateAccessToken(UUID userId, List<UUID> familyIds) {
        return buildToken(userId, familyIds, accessTokenExpiryMs, "access");
    }

    public String generateRefreshToken(UUID userId, List<UUID> familyIds) {
        return buildToken(userId, familyIds, refreshTokenExpiryMs, "refresh");
    }

    private String buildToken(UUID userId, List<UUID> familyIds, long expiryMs, String type) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("familyIds", familyIds.stream().map(UUID::toString).toList())
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMs))
                .signWith(key)
                .compact();
    }

    public Claims validateAndParseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    @SuppressWarnings("unchecked")
    public List<UUID> extractFamilyIds(Claims claims) {
        List<String> raw = claims.get("familyIds", List.class);
        if (raw == null) return List.of();
        return raw.stream().map(UUID::fromString).toList();
    }

    public long getAccessTokenExpiryMs() {
        return accessTokenExpiryMs;
    }
}
