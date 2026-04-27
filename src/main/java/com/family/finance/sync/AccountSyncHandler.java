package com.family.finance.sync;

import com.family.finance.dto.sync.SyncTableChanges;
import com.family.finance.entity.Account;
import com.family.finance.entity.User;
import com.family.finance.repository.AccountRepository;
import com.family.finance.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountSyncHandler implements SyncTableHandler {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final SyncConflictLogger conflictLogger;
    private final EntityManager entityManager;

    @Override
    public String tableName() {
        return "accounts";
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

        // Idempotent: if already exists, treat as update
        Optional<Account> existing = accountRepository.findByIdAndTenant(id, familyIds, user.getId());
        if (existing.isPresent()) {
            handleUpdate(raw, user, familyIds);
            return;
        }

        Account account = new Account();
        account.setId(id);
        if (!familyIds.isEmpty()) {
            account.setFamily(entityManager.getReference(
                    com.family.finance.entity.Family.class, familyIds.get(0)));
        } else {
            account.setUser(user);
        }
        applyFields(account, raw);
        accountRepository.save(account);
    }

    private void handleUpdate(Map<String, Object> raw, User user, List<UUID> familyIds) {
        UUID id = parseUuid(raw.get("id"));
        if (id == null) return;

        accountRepository.findByIdAndTenant(id, familyIds, user.getId()).ifPresentOrElse(
                existing -> {
                    // Delete latch: never update a soft-deleted record
                    if (existing.getDeletedAt() != null) {
                        log.info("Sync push: ignoring update for deleted account {}", id);
                        return;
                    }

                    // Version conflict detection
                    long clientVersion = toLong(raw.get("version"), 0L);
                    long serverVersion = existing.getVersion();
                    if (clientVersion < serverVersion) {
                        conflictLogger.log(
                                tableName(), id, user,
                                clientVersion, serverVersion,
                                raw, toWireFormat(existing)
                        );
                        // Server wins — do NOT apply client changes
                        return;
                    }

                    applyFields(existing, raw);
                    accountRepository.save(existing);
                },
                () -> log.warn("Sync push: update for account {} denied (not found or wrong tenant)", id)
        );
    }

    private void handleDelete(String idStr, User user, List<UUID> familyIds) {
        UUID id = parseUuid(idStr);
        if (id == null) return;

        accountRepository.findByIdAndTenant(id, familyIds, user.getId()).ifPresentOrElse(
                account -> {
                    if (account.getDeletedAt() == null) {
                        account.setDeletedAt(Instant.now());
                        account.setUpdatedAt(Instant.now());
                        accountRepository.save(account);
                    }
                },
                () -> log.warn("Sync push: delete for account {} denied (not found or wrong tenant)", id)
        );
    }

    @Override
    public SyncTableChanges buildPull(Instant since, Instant until,
                                       UUID userId, List<UUID> familyIds) {
        List<Account> modified = accountRepository.findModifiedForSync(since, until, familyIds, userId);

        List<Map<String, Object>> created = new ArrayList<>();
        List<Map<String, Object>> updated = new ArrayList<>();
        List<String> deleted = new ArrayList<>();

        for (Account a : modified) {
            if (a.getDeletedAt() != null && !a.getDeletedAt().isBefore(since)) {
                deleted.add(a.getId().toString());
            } else if (a.getCreatedAt().isAfter(since)) {
                created.add(toWireFormat(a));
            } else {
                updated.add(toWireFormat(a));
            }
        }

        return new SyncTableChanges(created, updated, deleted);
    }

    @Override
    public Map<String, Object> toWireFormat(Object entity) {
        Account a = (Account) entity;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("name", a.getName());
        m.put("type", a.getType() != null ? a.getType().name() : null);
        m.put("balance", a.getBalance() != null ? a.getBalance().toPlainString() : "0.00");
        m.put("currency", a.getCurrency());
        m.put("family_id", a.getFamily() != null ? a.getFamily().getId().toString() : null);
        m.put("user_id", a.getUser() != null ? a.getUser().getId().toString() : null);
        m.put("created_at", a.getCreatedAt() != null ? a.getCreatedAt().toEpochMilli() : null);
        m.put("updated_at", a.getUpdatedAt() != null ? a.getUpdatedAt().toEpochMilli() : null);
        m.put("deleted_at", a.getDeletedAt() != null ? a.getDeletedAt().toEpochMilli() : null);
        m.put("version", a.getVersion());
        return m;
    }

    // ----- Field mapping -----

    private void applyFields(Account a, Map<String, Object> raw) {
        if (raw.containsKey("name")) a.setName((String) raw.get("name"));
        if (raw.containsKey("type")) {
            try {
                a.setType(Account.AccountType.valueOf(
                        ((String) raw.get("type")).toUpperCase()));
            } catch (IllegalArgumentException e) {
                a.setType(Account.AccountType.CHECKING);
            }
        }
        if (raw.containsKey("balance")) {
            Object bal = raw.get("balance");
            if (bal instanceof String s) {
                try { a.setBalance(new BigDecimal(s)); } catch (NumberFormatException ignored) {}
            } else if (bal instanceof Number n) {
                // Accept numbers from wire but convert cleanly — never store float directly
                a.setBalance(new BigDecimal(n.toString()));
            }
        }
        if (raw.containsKey("currency")) a.setCurrency((String) raw.get("currency"));
        a.setUpdatedAt(Instant.now());
    }

    // ----- Helpers -----

    private static UUID parseUuid(Object val) {
        if (val instanceof String s) {
            try { return UUID.fromString(s); }
            catch (IllegalArgumentException e) {
                log.warn("AccountSyncHandler: invalid UUID '{}'", s);
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
