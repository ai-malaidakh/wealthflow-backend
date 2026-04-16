package com.family.finance.repository;

import com.family.finance.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    // Returns categories belonging to any of the given family IDs (shared categories)
    @Query("SELECT c FROM Category c WHERE c.family.id IN :familyIds AND c.deletedAt IS NULL")
    List<Category> findByFamilyIds(@Param("familyIds") List<UUID> familyIds);

    // Returns personal categories belonging to a specific user
    @Query("SELECT c FROM Category c WHERE c.user.id = :userId AND c.deletedAt IS NULL")
    List<Category> findByUserId(@Param("userId") UUID userId);

    // Tenant-scoped lookup — only returns category if it belongs to one of caller's families or is owned by caller
    @Query("""
        SELECT c FROM Category c
        WHERE c.id = :id
          AND c.deletedAt IS NULL
          AND (c.family.id IN :familyIds OR c.user.id = :userId)
        """)
    Optional<Category> findByIdAndTenant(
            @Param("id") UUID id,
            @Param("familyIds") List<UUID> familyIds,
            @Param("userId") UUID userId
    );

    /**
     * Pull query for WatermelonDB sync.
     * Returns all categories (including soft-deleted) modified in (since, until]
     * for the caller's tenant.
     */
    @Query("""
        SELECT c FROM Category c
        WHERE c.updatedAt > :since
          AND c.updatedAt <= :until
          AND (c.family.id IN :familyIds OR c.user.id = :userId)
        """)
    List<Category> findModifiedForSync(
            @Param("since") Instant since,
            @Param("until") Instant until,
            @Param("familyIds") List<UUID> familyIds,
            @Param("userId") UUID userId
    );

    @Query("SELECT c FROM Category c WHERE c.name = :name AND c.type = :type AND c.deletedAt IS NULL AND (c.family.id IN :familyIds OR c.user.id = :userId)")
    Optional<Category> findByNameAndTypeAndTenant(
            @Param("name") String name,
            @Param("type") Category.CategoryType type,
            @Param("familyIds") List<UUID> familyIds,
            @Param("userId") UUID userId
    );
}
