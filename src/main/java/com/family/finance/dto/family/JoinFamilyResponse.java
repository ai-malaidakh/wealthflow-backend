package com.family.finance.dto.family;

import java.util.UUID;

public record JoinFamilyResponse(
        UUID familyId,
        String familyName,
        String role
) {}
