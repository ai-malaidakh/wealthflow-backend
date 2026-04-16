package com.family.finance.sync;

import com.family.finance.dto.sync.SyncTableChanges;
import com.family.finance.entity.Account;
import com.family.finance.entity.Category;
import com.family.finance.entity.Transaction;
import com.family.finance.entity.User;
import com.family.finance.repository.AccountRepository;
import com.family.finance.repository.CategoryRepository;
import com.family.finance.repository.TransactionRepository;
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
public class TransactionSyncHandler implements SyncTableHandler {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final SyncConflictLogger conflictLogger;
    private final EntityManager entityManager;

    @Override
    public String tableName() {
        return "transactions";
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

        // Idempotent: treat duplicate create as update
        Optional<Transaction> existing = transactionRepository.findByIdAndTenant(id, familyIds, user.getId());
        if (existing.isPresent()) {
            handleUpdate(raw, user, familyIds);
            return;
        }

        UUID accountId = parseUuid(raw.get("account_id"));
        if (accountId == null) {
            log.warn("TransactionSyncHandler: create rejected — missing account_id for tx {}", id);
            return;
        }

        // Account must belong to caller's tenant
        Optional<Account> account = accountRepository.findByIdAndTenant(
                accountId, familyIds.isEmpty() ? List.of(UUID.randomUUID()) : familyIds, user.getId());
        if (account.isEmpty()) {
            log.warn("TransactionSyncHandler: create rejected — account {} not in tenant (tx {})", accountId, id);
            return;
        }

        Transaction tx = new Transaction();
        tx.setId(id);
        tx.setAccount(account.get());
        applyFields(tx, raw, familyIds, user.getId());
        transactionRepository.save(tx);
    }

    private void handleUpdate(Map<String, Object> raw, User user, List<UUID> familyIds) {
        UUID id = parseUuid(raw.get("id"));
        if (id == null) return;

        transactionRepository.findByIdAndTenant(id, familyIds, user.getId()).ifPresentOrElse(
                existing -> {
                    // Delete latch: query already excludes deleted rows, but guard here for clarity
                    if (existing.getDeletedAt() != null) {
                        log.info("TransactionSyncHandler: ignoring update for deleted tx {}", id);
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

                    applyFields(existing, raw, familyIds, user.getId());
                    transactionRepository.save(existing);
                },
                () -> log.warn("TransactionSyncHandler: update denied — tx {} not found or wrong tenant", id)
        );
    }

    private void handleDelete(String idStr, User user, List<UUID> familyIds) {
        UUID id = parseUuid(idStr);
        if (id == null) return;

        transactionRepository.findByIdAndTenant(id, familyIds, user.getId()).ifPresentOrElse(
                tx -> {
                    if (tx.getDeletedAt() == null) {
                        tx.setDeletedAt(Instant.now());
                        tx.setUpdatedAt(Instant.now());
                        transactionRepository.save(tx);
                    }
                },
                () -> log.warn("TransactionSyncHandler: delete denied — tx {} not found or wrong tenant", id)
        );
    }

    @Override
    public SyncTableChanges buildPull(Instant since, Instant until, UUID userId, List<UUID> familyIds) {
        List<Transaction> modified = transactionRepository.findModifiedForSync(since, until, familyIds, userId);

        List<Map<String, Object>> created = new ArrayList<>();
        List<Map<String, Object>> updated = new ArrayList<>();
        List<String> deleted = new ArrayList<>();

        for (Transaction t : modified) {
            if (t.getDeletedAt() != null && !t.getDeletedAt().isBefore(since)) {
                deleted.add(t.getId().toString());
            } else if (t.getCreatedAt().isAfter(since)) {
                created.add(toWireFormat(t));
            } else {
                updated.add(toWireFormat(t));
            }
        }

        return new SyncTableChanges(created, updated, deleted);
    }

    @Override
    public Map<String, Object> toWireFormat(Object entity) {
        Transaction t = (Transaction) entity;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId().toString());
        m.put("account_id", t.getAccount() != null ? t.getAccount().getId().toString() : null);
        m.put("category_id", t.getCategory() != null ? t.getCategory().getId().toString() : null);
        m.put("amount", t.getAmount() != null ? t.getAmount().toPlainString() : "0.00");
        m.put("currency", t.getCurrency());
        m.put("description", t.getDescription());
        // date as epoch milliseconds (start of day UTC) for WatermelonDB compatibility
        m.put("date", t.getDate() != null
                ? t.getDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                : null);
        m.put("created_at", t.getCreatedAt() != null ? t.getCreatedAt().toEpochMilli() : null);
        m.put("updated_at", t.getUpdatedAt() != null ? t.getUpdatedAt().toEpochMilli() : null);
        m.put("deleted_at", t.getDeletedAt() != null ? t.getDeletedAt().toEpochMilli() : null);
        m.put("version", t.getVersion());
        return m;
    }

    // ----- Field mapping -----

    private void applyFields(Transaction t, Map<String, Object> raw, List<UUID> familyIds, UUID userId) {
        if (raw.containsKey("amount")) {
            Object amt = raw.get("amount");
            if (amt instanceof String s) {
                try { t.setAmount(new BigDecimal(s)); } catch (NumberFormatException ignored) {}
            } else if (amt instanceof Number n) {
                t.setAmount(new BigDecimal(n.toString()));
            }
        }
        if (raw.containsKey("currency")) t.setCurrency((String) raw.get("currency"));
        if (raw.containsKey("description")) t.setDescription((String) raw.get("description"));
        if (raw.containsKey("date")) {
            LocalDate date = parseDate(raw.get("date"));
            if (date != null) t.setDate(date);
        }
        if (raw.containsKey("category_id")) {
            UUID catId = parseUuid(raw.get("category_id"));
            if (catId != null) {
                categoryRepository.findByIdAndTenant(catId, familyIds, userId)
                        .ifPresent(t::setCategory);
            } else {
                t.setCategory(null);
            }
        }
        t.setUpdatedAt(Instant.now());
    }

    // ----- Helpers -----

    private static LocalDate parseDate(Object val) {
        if (val instanceof String s) {
            try { return LocalDate.parse(s); } catch (Exception ignored) {}
        }
        if (val instanceof Number n) {
            // Epoch milliseconds → LocalDate (UTC)
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
                log.warn("TransactionSyncHandler: invalid UUID '{}'", s);
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
