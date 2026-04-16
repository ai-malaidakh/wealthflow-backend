package com.family.finance.dto.family;

import jakarta.validation.constraints.NotBlank;

public record JoinFamilyRequest(
        @NotBlank String code
) {}
