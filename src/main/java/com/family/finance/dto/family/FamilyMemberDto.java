package com.family.finance.dto.family;

import java.time.Instant;
import java.util.UUID;

public record FamilyMemberDto(
        UUID memberId,
        UUID userId,
        String email,
        String displayName,
        String role,
        Instant joinedAt
) {}
