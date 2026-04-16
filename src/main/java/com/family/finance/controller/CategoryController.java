package com.family.finance.controller;

import com.family.finance.entity.Category;
import com.family.finance.entity.Family;
import com.family.finance.entity.User;
import com.family.finance.repository.CategoryRepository;
import com.family.finance.repository.FamilyRepository;
import com.family.finance.repository.UserRepository;
import com.family.finance.security.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;

    record CategoryRequest(
            @NotBlank String name,
            Category.CategoryType type,
            UUID familyId   // null = personal category
    ) {}

    record CategoryResponse(UUID id, String name, String type, UUID familyId, UUID userId) {
        static CategoryResponse from(Category c) {
            return new CategoryResponse(
                    c.getId(), c.getName(), c.getType().name(),
                    c.getFamily() != null ? c.getFamily().getId() : null,
                    c.getUser() != null ? c.getUser().getId() : null
            );
        }
    }

    @GetMapping
    public List<CategoryResponse> list() {
        UUID userId = TenantContext.getCurrentUserId();
        List<UUID> familyIds = TenantContext.getCurrentFamilyIds();

        List<Category> categories = new ArrayList<>();
        if (!familyIds.isEmpty()) {
            categories.addAll(categoryRepository.findByFamilyIds(familyIds));
        }
        categories.addAll(categoryRepository.findByUserId(userId));
        return categories.stream().map(CategoryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable UUID id) {
        return categoryRepository.findByIdAndTenant(id,
                        TenantContext.getCurrentFamilyIds(),
                        TenantContext.getCurrentUserId())
                .map(CategoryResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName(request.name());
        category.setType(request.type() != null ? request.type() : Category.CategoryType.EXPENSE);

        if (request.familyId() != null) {
            // Verify caller belongs to this family
            if (!TenantContext.getCurrentFamilyIds().contains(request.familyId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this family");
            }
            Family family = familyRepository.findById(request.familyId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
            category.setFamily(family);
        } else {
            User user = userRepository.findById(TenantContext.getCurrentUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            category.setUser(user);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CategoryResponse.from(categoryRepository.save(category)));
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        Category category = categoryRepository.findByIdAndTenant(id,
                        TenantContext.getCurrentFamilyIds(),
                        TenantContext.getCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        category.setName(request.name());
        if (request.type() != null) category.setType(request.type());

        if (request.familyId() != null) {
            if (!TenantContext.getCurrentFamilyIds().contains(request.familyId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this family");
            }
            Family family = familyRepository.findById(request.familyId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
            category.setFamily(family);
            category.setUser(null);
        }

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        Category category = categoryRepository.findByIdAndTenant(id,
                        TenantContext.getCurrentFamilyIds(),
                        TenantContext.getCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        category.setDeletedAt(java.time.Instant.now());
        categoryRepository.save(category);
    }
}
