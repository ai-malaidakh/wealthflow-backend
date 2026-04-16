package com.family.finance.dto.family;

import com.family.finance.entity.FamilyMember;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
        @NotNull FamilyMember.Role role
) {}
