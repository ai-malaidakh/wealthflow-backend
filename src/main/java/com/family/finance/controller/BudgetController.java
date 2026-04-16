package com.family.finance.controller;

import com.family.finance.entity.Budget;
import com.family.finance.entity.Category;
import com.family.finance.entity.Family;
import com.family.finance.entity.FamilyMember;
import com.family.finance.repository.BudgetRepository;
import com.family.finance.repository.CategoryRepository;
import com.family.finance.repository.FamilyMemberRepository;
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
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final FamilyMemberRepository familyMemberRepository;

    record BudgetRequest(
            @NotNull UUID familyId,
            @NotNull UUID categoryId,
            @NotNull BigDecimal amount,
            String currency,
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd
    ) {}

    record BudgetResponse(
            UUID id, UUID familyId, UUID categoryId, BigDecimal amount,
            String currency, LocalDate periodStart, LocalDate periodEnd
    ) {
        static BudgetResponse from(Budget b) {
            return new BudgetResponse(
                    b.getId(), b.getFamily().getId(), b.getCategory().getId(),
                    b.getAmount(), b.getCurrency(), b.getPeriodStart(), b.getPeriodEnd()
            );
        }
    }

    @GetMapping
    public List<BudgetResponse> list() {
        List<UUID> familyIds = TenantContext.getCurrentFamilyIds();
        List<Budget> budgets = budgetRepository.findByFamilyIds(familyIds);
        return budgets.stream().map(BudgetResponse::from).toList();
    }

    @GetMapping("/{id}")
    public BudgetResponse get(@PathVariable UUID id) {
        return budgetRepository.findByIdAndTenant(id,
                        TenantContext.getCurrentFamilyIds())
                .map(BudgetResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/category/{categoryId}")
    public List<BudgetResponse> listByCategory(@PathVariable UUID categoryId) {
        List<UUID> familyIds = TenantContext.getCurrentFamilyIds();
        List<Budget> budgets = budgetRepository.findByFamilyIdsAndCategoryId(familyIds, categoryId);
        return budgets.stream().map(BudgetResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> create(@Valid @RequestBody BudgetRequest request) {
        // Verify caller belongs to this family
        if (!TenantContext.getCurrentFamilyIds().contains(request.familyId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this family");
        }

        // Verify active membership and load family entity
        Optional<FamilyMember> membershipOpt = familyMemberRepository.findByFamilyIdAndUserId(
                request.familyId(), TenantContext.getCurrentUserId());
        if (membershipOpt.isEmpty() || membershipOpt.get().getDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found or inactive");
        }

        Category category = categoryRepository.findByIdAndTenant(request.categoryId(),
                        TenantContext.getCurrentFamilyIds(), TenantContext.getCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        Budget budget = new Budget();
        budget.setId(UUID.randomUUID());
        budget.setFamily(membershipOpt.get().getFamily());
        budget.setCategory(category);
        budget.setAmount(request.amount());
        budget.setCurrency(request.currency() != null ? request.currency() : "USD");
        budget.setPeriodStart(request.periodStart());
        budget.setPeriodEnd(request.periodEnd());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BudgetResponse.from(budgetRepository.save(budget)));
    }

    @PutMapping("/{id}")
    public BudgetResponse update(@PathVariable UUID id, @Valid @RequestBody BudgetRequest request) {
        Budget budget = budgetRepository.findByIdAndTenant(id,
                        TenantContext.getCurrentFamilyIds())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Verify caller belongs to this family
        if (!TenantContext.getCurrentFamilyIds().contains(request.familyId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this family");
        }

        // Verify family matches existing budget
        if (!budget.getFamily().getId().equals(request.familyId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot change family for existing budget");
        }

        Category category = categoryRepository.findByIdAndTenant(request.categoryId(),
                        TenantContext.getCurrentFamilyIds(), TenantContext.getCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        budget.setCategory(category);
        budget.setAmount(request.amount());
        budget.setCurrency(request.currency() != null ? request.currency() : "USD");
        budget.setPeriodStart(request.periodStart());
        budget.setPeriodEnd(request.periodEnd());

        return BudgetResponse.from(budgetRepository.save(budget));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        Budget budget = budgetRepository.findByIdAndTenant(id,
                        TenantContext.getCurrentFamilyIds())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        budget.setDeletedAt(java.time.Instant.now());
        budgetRepository.save(budget);
    }

    private void validateCategoryOwnership(Category category) {
        UUID userId = TenantContext.getCurrentUserId();
        List<UUID> familyIds = TenantContext.getCurrentFamilyIds();

        // Category must be owned by this user or one of their families
        boolean isOwned = category.getUser() != null && category.getUser().getId().equals(userId);
        boolean isFamilyShared = category.getFamily() != null && familyIds.contains(category.getFamily().getId());

        if (!isOwned && !isFamilyShared) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Category not accessible");
        }
    }
}
