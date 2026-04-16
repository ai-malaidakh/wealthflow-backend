package com.family.finance.repository;

import com.family.finance.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    // Returns budgets for any of the given family IDs
    @Query("SELECT b FROM Budget b WHERE b.family.id IN :familyIds AND b.deletedAt IS NULL")
    List<Budget> findByFamilyIds(@Param("familyIds") List<UUID> familyIds);

    // Returns budgets for a specific category within the given families
    @Query("""
        SELECT b FROM Budget b
        WHERE b.family.id IN :familyIds
          AND b.category.id = :categoryId
          AND b.deletedAt IS NULL
        """)
    List<Budget> findByFamilyIdsAndCategoryId(
            @Param("familyIds") List<UUID> familyIds,
            @Param("categoryId") UUID categoryId
    );

    // Tenant-scoped lookup — only returns budget if its family belongs to caller
    @Query("""
        SELECT b FROM Budget b
        WHERE b.id = :id
          AND b.deletedAt IS NULL
          AND b.family.id IN :familyIds
        """)
    Optional<Budget> findByIdAndTenant(
            @Param("id") UUID id,
            @Param("familyIds") List<UUID> familyIds
    );

    /**
     * Pull query for WatermelonDB sync.
     * Returns all budgets (including soft-deleted) modified in (since, until]
     * for the caller's families. Budgets are always family-scoped.
     */
    @Query("""
        SELECT b FROM Budget b
        WHERE b.updatedAt > :since
          AND b.updatedAt <= :until
          AND b.family.id IN :familyIds
        """)
    List<Budget> findModifiedForSync(
            @Param("since") Instant since,
            @Param("until") Instant until,
            @Param("familyIds") List<UUID> familyIds
    );
}
