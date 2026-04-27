package com.family.finance.sync;

import com.family.finance.dto.sync.SyncTableChanges;
import com.family.finance.entity.Budget;
import com.family.finance.entity.User;
import com.family.finance.repository.BudgetRepository;
import com.family.finance.repository.CategoryRepository;
import com.family.finance.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class BudgetSyncHandler implements SyncTableHandler {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final SyncConflictLogger conflictLogger;
    private final EntityManager entityManager;

    @Override
    public String tableName() {
        return "budgets";
    }

    @Override
    public void applyPush(SyncTableChanges changes, UUID userId, List<UUID> familyIds) {
        // Budgets are always family-scoped; personal users cannot push budgets
        if (familyIds.isEmpty()) {
            log.warn("BudgetSyncHandler: push rejected — no family context for user {}", userId);
            return;
        }
        User user = userRepository.getReferenceById(userId);

        for (Map<String, Object> raw : changes.created()) {
            handleCreate(raw, user, familyIds);
        }
        for (Map<String, Object> raw : changes.updated()) {
            handleUpdate(raw, user, familyIds);
        }
        for (String id : changes.deleted()) {
            handleDelete(id, familyIds);
        }
    }

    private void handleCreate(Map<String, Object> raw, User user, List<UUID> familyIds) {
        UUID id = parseUuid(raw.get("id"));
        if (id == null) return;

        Optional<Budget> existing = budgetRepository.findByIdAndTenant(id, familyIds);
        if (existing.isPresent()) {
            handleUpdate(raw, user, familyIds);
            return;
        }

        UUID categoryId = parseUuid(raw.get("category_id"));
        if (categoryId == null) {
            log.warn("BudgetSyncHandler: create rejected — missing category_id for budget {}", id);
            return;
        }

        // Category must be in caller's tenant
        if (categoryRepository.findByIdAndTenant(categoryId, familyIds, user.getId()).isEmpty()) {
            log.warn("BudgetSyncHandler: create rejected — category {} not in tenant (budget {})", categoryId, id);
            return;
        }

        Budget budget = new Budget();
        budget.setId(id);
        // Use the first family ID as the owning family
        budget.setFamily(entityManager.getReference(
                com.family.finance.entity.Family.class, familyIds.get(0)));
        budget.setCategory(entityManager.getReference(
                com.family.finance.entity.Category.class, categoryId));
        applyFields(budget, raw);
        budgetRepository.save(budget);
    }

    private void handleUpdate(Map<String, Object> raw, User user, List<UUID> familyIds) {
        UUID id = parseUuid(raw.get("id"));
        if (id == null) return;

        budgetRepository.findByIdAndTenant(id, familyIds).ifPresentOrElse(
                existing -> {
                    if (existing.getDeletedAt() != null) {
                        log.info("BudgetSyncHandler: ignoring update for deleted budget {}", id);
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
                    budgetRepository.save(existing);
                },
                () -> log.warn("BudgetSyncHandler: update denied — budget {} not found or wrong tenant", id)
        );
    }

    private void handleDelete(String idStr, List<UUID> familyIds) {
        UUID id = parseUuid(idStr);
        if (id == null) return;

        budgetRepository.findByIdAndTenant(id, familyIds).ifPresentOrElse(
                budget -> {
                    if (budget.getDeletedAt() == null) {
                        budget.setDeletedAt(Instant.now());
                        budget.setUpdatedAt(Instant.now());
                        budgetRepository.save(budget);
                    }
                },
                () -> log.warn("BudgetSyncHandler: delete denied — budget {} not found or wrong tenant", id)
        );
    }

    @Override
    public SyncTableChanges buildPull(Instant since, Instant until, UUID userId, List<UUID> familyIds) {
        if (familyIds.isEmpty()) {
            return new SyncTableChanges(List.of(), List.of(), List.of());
        }

        List<Budget> modified = budgetRepository.findModifiedForSync(since, until, familyIds);

        List<Map<String, Object>> updated = new ArrayList<>();
        List<String> deleted = new ArrayList<>();

        for (Budget b : modified) {
            if (b.getDeletedAt() != null && !b.getDeletedAt().isBefore(since)) {
                deleted.add(b.getId().toString());
            } else {
                updated.add(toWireFormat(b));
            }
        }

        return new SyncTableChanges(List.of(), updated, deleted);
    }

    @Override
    public Map<String, Object> toWireFormat(Object entity) {
        Budget b = (Budget) entity;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId().toString());
        m.put("family_id", b.getFamily() != null ? b.getFamily().getId().toString() : null);
        m.put("category_id", b.getCategory() != null ? b.getCategory().getId().toString() : null);
        m.put("amount", b.getAmount() != null ? b.getAmount().toPlainString() : "0.00");
        m.put("currency", b.getCurrency());
        m.put("period_start", b.getPeriodStart() != null
                ? b.getPeriodStart().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                : null);
        m.put("period_end", b.getPeriodEnd() != null
                ? b.getPeriodEnd().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                : null);
        m.put("created_at", b.getCreatedAt() != null ? b.getCreatedAt().toEpochMilli() : null);
        m.put("updated_at", b.getUpdatedAt() != null ? b.getUpdatedAt().toEpochMilli() : null);
        m.put("deleted_at", b.getDeletedAt() != null ? b.getDeletedAt().toEpochMilli() : null);
        m.put("version", b.getVersion());
        return m;
    }

    // ----- Field mapping -----

    private void applyFields(Budget b, Map<String, Object> raw) {
        if (raw.containsKey("amount")) {
            Object amt = raw.get("amount");
            if (amt instanceof String s) {
                try { b.setAmount(new BigDecimal(s)); } catch (NumberFormatException ignored) {}
            } else if (amt instanceof Number n) {
                b.setAmount(new BigDecimal(n.toString()));
            }
        }
        if (raw.containsKey("currency")) b.setCurrency((String) raw.get("currency"));
        if (raw.containsKey("period_start")) {
            LocalDate d = parseDate(raw.get("period_start"));
            if (d != null) b.setPeriodStart(d);
        }
        if (raw.containsKey("period_end")) {
            LocalDate d = parseDate(raw.get("period_end"));
            if (d != null) b.setPeriodEnd(d);
        }
        b.setUpdatedAt(Instant.now());
    }

    // ----- Helpers -----

    private static LocalDate parseDate(Object val) {
        if (val instanceof String s) {
            try { return LocalDate.parse(s); } catch (Exception ignored) {}
        }
        if (val instanceof Number n) {
            return Instant.ofEpochMilli(n.longValue())
                    .atZone(java.time.ZoneOffset.UTC)
                    .toLocalDate();
        }
        return null;
    }

    private static UUID parseUuid(Object val) {
        if (val instanceof String s) {
            try { return UUID.fromString(s); }
            catch (IllegalArgumentException e) {
                log.warn("BudgetSyncHandler: invalid UUID '{}'", s);
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
