package com.family.finance.dto.family;

import java.time.Instant;
import java.util.UUID;

public record CreateInviteResponse(
        UUID inviteId,
        String code,
        Instant expiresAt
) {}
