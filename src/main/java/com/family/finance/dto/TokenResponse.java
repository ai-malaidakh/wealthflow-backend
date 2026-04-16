package com.family.finance.dto;

import java.util.List;
import java.util.UUID;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        List<UUID> familyIds
) {
    public static TokenResponse of(String accessToken, String refreshToken, long expiresInMs,
                                    UUID userId, List<UUID> familyIds) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInMs / 1000, userId, familyIds);
    }
}
