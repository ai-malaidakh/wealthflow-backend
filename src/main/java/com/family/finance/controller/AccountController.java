package com.family.finance.controller;

import com.family.finance.entity.Account;
import com.family.finance.entity.Family;
import com.family.finance.entity.User;
import com.family.finance.repository.AccountRepository;
import com.family.finance.repository.FamilyRepository;
import com.family.finance.repository.UserRepository;
import com.family.finance.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;
    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;

    record AccountRequest(
            @NotBlank String name,
            Account.AccountType type,
            @NotNull BigDecimal balance,
            @NotBlank String currency,
            UUID familyId   // null = personal account
    ) {}

    record AccountResponse(UUID id, String name, String type, BigDecimal balance,
                           String currency, UUID familyId, UUID userId) {
        static AccountResponse from(Account a) {
            return new AccountResponse(
                    a.getId(), a.getName(), a.getType().name(), a.getBalance(), a.getCurrency(),
                    a.getFamily() != null ? a.getFamily().getId() : null,
                    a.getUser() != null ? a.getUser().getId() : null
            );
        }
    }

    @GetMapping
    public List<AccountResponse> list() {
        UUID userId = TenantContext.getCurrentUserId();
        List<UUID> familyIds = TenantContext.getCurrentFamilyIds();

        List<Account> accounts = new ArrayList<>();
        if (!familyIds.isEmpty()) {
            accounts.addAll(accountRepository.findByFamilyIds(familyIds));
        }
        accounts.addAll(accountRepository.findByUserId(userId));
        return accounts.stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return accountRepository.findByIdAndTenant(id,
                        TenantContext.getCurrentFamilyIds(),
                        TenantContext.getCurrentUserId())
                .map(AccountResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody AccountRequest request) {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setName(request.name());
        account.setType(request.type() != null ? request.type() : Account.AccountType.CHECKING);
        account.setBalance(request.balance());
        account.setCurrency(request.currency());

        if (request.familyId() != null) {
            // Verify caller belongs to this family
            if (!TenantContext.getCurrentFamilyIds().contains(request.familyId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this family");
            }
            Family family = familyRepository.findById(request.familyId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
            account.setFamily(family);
        } else {
            User user = userRepository.findById(TenantContext.getCurrentUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            account.setUser(user);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AccountResponse.from(accountRepository.save(account)));
    }

    @PutMapping("/{id}")
    public AccountResponse update(@PathVariable UUID id, @Valid @RequestBody AccountRequest request) {
        Account account = accountRepository.findByIdAndTenant(id,
                        TenantContext.getCurrentFamilyIds(),
                        TenantContext.getCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        account.setName(request.name());
        if (request.type() != null) account.setType(request.type());
        account.setBalance(request.balance());
        account.setCurrency(request.currency());

        if (request.familyId() != null) {
            if (!TenantContext.getCurrentFamilyIds().contains(request.familyId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this family");
            }
            Family family = familyRepository.findById(request.familyId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
            account.setFamily(family);
            account.setUser(null);
        }

        return AccountResponse.from(accountRepository.save(account));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        Account account = accountRepository.findByIdAndTenant(id,
                        TenantContext.getCurrentFamilyIds(),
                        TenantContext.getCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        account.setDeletedAt(java.time.Instant.now());
        accountRepository.save(account);
    }
}
