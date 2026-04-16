package com.family.finance.controller;

import com.family.finance.entity.Account;
import com.family.finance.entity.Category;
import com.family.finance.entity.Transaction;
import com.family.finance.repository.AccountRepository;
import com.family.finance.repository.CategoryRepository;
import com.family.finance.repository.TransactionRepository;
import com.family.finance.repository.FamilyRepository;
import com.family.finance.repository.UserRepository;
import com.family.finance.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;

    record TransactionRequest(
            @NotNull UUID accountId,
            UUID categoryId,
            @NotNull BigDecimal amount,
            String description,
            @NotNull LocalDate date
    ) {}

    record TransactionResponse(
            UUID id, UUID accountId, UUID categoryId, BigDecimal amount,
            String currency, String description, LocalDate date
    ) {
        static TransactionResponse from(Transaction t) {
            return new TransactionResponse(
                    t.getId(), t.getAccount().getId(),
                    t.getCategory() != null ? t.getCategory().getId() : null,
                    t.getAmount(), t.getCurrency(), t.getDescription(), t.getDate()
            );
        }
    }

    @GetMapping
    public List<TransactionResponse> list() {
        UUID userId = TenantContext.getCurrentUserId();
        List<UUID> familyIds = TenantContext.getCurrentFamilyIds();

        List<Transaction> transactions = transactionRepository.findByFamilyIds(familyIds);
        transactions.addAll(transactionRepository.findByUserId(userId));
        return transactions.stream().map(TransactionResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable UUID id) {
        return transactionRepository.findByIdAndTenant(id,
                        TenantContext.getCurrentFamilyIds(),
                        TenantContext.getCurrentUserId())
                .map(TransactionResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/account/{accountId}")
    public List<TransactionResponse> listByAccount(@PathVariable UUID accountId) {
        // Verify account belongs to caller's tenant
        Account account = accountRepository.findByIdAndTenant(accountId,
                        TenantContext.getCurrentFamilyIds(),
                        TenantContext.getCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        
        List<Transaction> transactions = transactionRepository.findByAccountId(accountId);
        return transactions.stream().map(TransactionResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        // Verify account belongs to caller's tenant
        Account account = accountRepository.findByIdAndTenant(request.accountId(),
                        TenantContext.getCurrentFamilyIds(),
                        TenantContext.getCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setAccount(account);
        transaction.setAmount(request.amount());
        transaction.setDescription(request.description());
        transaction.setDate(request.date());

        if (request.categoryId() != null) {
            Category category = categoryRepository.findByIdAndTenant(request.categoryId(),
                            TenantContext.getCurrentFamilyIds(), TenantContext.getCurrentUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
            transaction.setCategory(category);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TransactionResponse.from(transactionRepository.save(transaction)));
    }

    @PutMapping("/{id}")
    public TransactionResponse update(@PathVariable UUID id, @Valid @RequestBody TransactionRequest request) {
        Transaction transaction = transactionRepository.findByIdAndTenant(id,
                        TenantContext.getCurrentFamilyIds(),
                        TenantContext.getCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Verify account belongs to caller's tenant
        Account account = accountRepository.findByIdAndTenant(request.accountId(),
                        TenantContext.getCurrentFamilyIds(),
                        TenantContext.getCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        transaction.setAccount(account);
        transaction.setAmount(request.amount());
        transaction.setDescription(request.description());
        transaction.setDate(request.date());

        if (request.categoryId() != null) {
            Category category = categoryRepository.findByIdAndTenant(request.categoryId(),
                            TenantContext.getCurrentFamilyIds(), TenantContext.getCurrentUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
            transaction.setCategory(category);
        }

        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        Transaction transaction = transactionRepository.findByIdAndTenant(id,
                        TenantContext.getCurrentFamilyIds(),
                        TenantContext.getCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        transaction.setDeletedAt(java.time.Instant.now());
        transactionRepository.save(transaction);
    }
}
