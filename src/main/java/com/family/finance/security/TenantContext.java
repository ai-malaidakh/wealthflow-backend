package com.family.finance.security;

import java.util.List;
import java.util.UUID;

/**
 * Thread-local holder for the current authenticated user's tenant context.
 * Set by JwtAuthenticationFilter and cleared after request completion.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<List<UUID>> CURRENT_FAMILY_IDS = new ThreadLocal<>();

    private TenantContext() {}

    public static void setCurrentTenant(UUID userId, List<UUID> familyIds) {
        CURRENT_USER_ID.set(userId);
        CURRENT_FAMILY_IDS.set(familyIds);
    }

    public static UUID getCurrentUserId() {
        return CURRENT_USER_ID.get();
    }

    public static List<UUID> getCurrentFamilyIds() {
        List<UUID> ids = CURRENT_FAMILY_IDS.get();
        return ids != null ? ids : List.of();
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
        CURRENT_FAMILY_IDS.remove();
    }
}
