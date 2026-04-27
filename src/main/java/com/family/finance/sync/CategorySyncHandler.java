package com.family.finance.sync;

import com.family.finance.dto.sync.SyncTableChanges;
import com.family.finance.entity.Category;
import com.family.finance.entity.User;
import com.family.finance.repository.CategoryRepository;
import com.family.finance.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class CategorySyncHandler implements SyncTableHandler {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final SyncConflictLogger conflictLogger;
    private final EntityManager entityManager;

    @Override
    public String tableName() {
        return "categories";
    }

    @Override
    public void applyPush(SyncTableChanges changes, UUID userId, List<UUID> familyIds) {
        User user = userRepository.getReferenceById(userId);

        for (Map<String, Object> raw : changes.created()) {
            handleCreate(raw, user, familyIds);
        }
        for (Map<String, Object> raw : changes.updated()) {
            handleUpdate(raw, user, familyIds);
        }
        for (String id : changes.deleted()) {
            handleDelete(id, user, familyIds);
        }
    }

    private void handleCreate(Map<String, Object> raw, User user, List<UUID> familyIds) {
        UUID id = parseUuid(raw.get("id"));
        if (id == null) return;

        Optional<Category> existing = categoryRepository.findByIdAndTenant(id, familyIds, user.getId());
        if (existing.isPresent()) {
            handleUpdate(raw, user, familyIds);
            return;
        }

        Category category = new Category();
        category.setId(id);
        if (!familyIds.isEmpty()) {
            category.setFamily(entityManager.getReference(
                    com.family.finance.entity.Family.class, familyIds.get(0)));
        } else {
            category.setUser(user);
        }
        applyFields(category, raw);
        categoryRepository.save(category);
    }

    private void handleUpdate(Map<String, Object> raw, User user, List<UUID> familyIds) {
        UUID id = parseUuid(raw.get("id"));
        if (id == null) return;

        categoryRepository.findByIdAndTenant(id, familyIds, user.getId()).ifPresentOrElse(
                existing -> {
                    if (existing.getDeletedAt() != null) {
                        log.info("CategorySyncHandler: ignoring update for deleted category {}", id);
                        return;
                    }

                    long clientVersion = toLong(raw.get("version"), 0L);
                    long serverVersion = existing.getVersion();
                    if (clientVersion < serverVersion) {
                        conflictLogger.log(
                                tableName(), id, user,
                                clientVersion, serverVersion,
                                raw, toWireFormat(existing)
                        );
                        return;
                    }

                    applyFields(existing, raw);
                    categoryRepository.save(existing);
                },
                () -> log.warn("CategorySyncHandler: update denied — category {} not found or wrong tenant", id)
        );
    }

    private void handleDelete(String idStr, User user, List<UUID> familyIds) {
        UUID id = parseUuid(idStr);
        if (id == null) return;

        categoryRepository.findByIdAndTenant(id, familyIds, user.getId()).ifPresentOrElse(
                category -> {
                    if (category.getDeletedAt() == null) {
                        category.setDeletedAt(Instant.now());
                        category.setUpdatedAt(Instant.now());
                        categoryRepository.save(category);
                    }
                },
                () -> log.warn("CategorySyncHandler: delete denied — category {} not found or wrong tenant", id)
        );
    }

    @Override
    public SyncTableChanges buildPull(Instant since, Instant until, UUID userId, List<UUID> familyIds) {
        List<Category> modified = categoryRepository.findModifiedForSync(since, until, familyIds, userId);

        List<Map<String, Object>> updated = new ArrayList<>();
        List<String> deleted = new ArrayList<>();

        for (Category c : modified) {
            if (c.getDeletedAt() != null && !c.getDeletedAt().isBefore(since)) {
                deleted.add(c.getId().toString());
            } else {
                updated.add(toWireFormat(c));
            }
        }

        return new SyncTableChanges(List.of(), updated, deleted);
    }

    @Override
    public Map<String, Object> toWireFormat(Object entity) {
        Category c = (Category) entity;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId().toString());
        m.put("name", c.getName());
        m.put("type", c.getType() != null ? c.getType().name() : null);
        m.put("family_id", c.getFamily() != null ? c.getFamily().getId().toString() : null);
        m.put("user_id", c.getUser() != null ? c.getUser().getId().toString() : null);
        m.put("created_at", c.getCreatedAt() != null ? c.getCreatedAt().toEpochMilli() : null);
        m.put("updated_at", c.getUpdatedAt() != null ? c.getUpdatedAt().toEpochMilli() : null);
        m.put("deleted_at", c.getDeletedAt() != null ? c.getDeletedAt().toEpochMilli() : null);
        m.put("version", c.getVersion());
        return m;
    }

    // ----- Field mapping -----

    private void applyFields(Category c, Map<String, Object> raw) {
        if (raw.containsKey("name")) c.setName((String) raw.get("name"));
        if (raw.containsKey("type") && raw.get("type") != null) {
            try {
                c.setType(Category.CategoryType.valueOf(
                        ((String) raw.get("type")).toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("CategorySyncHandler: unknown type '{}' for category {}, keeping existing",
                        raw.get("type"), c.getId());
            }
        }
        c.setUpdatedAt(Instant.now());
    }

    // ----- Helpers -----

    private static UUID parseUuid(Object val) {
        if (val instanceof String s) {
            try { return UUID.fromString(s); }
            catch (IllegalArgumentException e) {
                log.warn("CategorySyncHandler: invalid UUID '{}'", s);
            }
        }
        return null;
    }

    private static long toLong(Object val, long defaultVal) {
        if (val instanceof Number n) return n.longValue();
        if (val instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }
}
