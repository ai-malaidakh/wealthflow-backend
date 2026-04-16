package com.family.finance.repository;

import com.family.finance.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    // Returns accounts belonging to any of the given family IDs (shared accounts)
    @Query("SELECT a FROM Account a WHERE a.family.id IN :familyIds AND a.deletedAt IS NULL")
    List<Account> findByFamilyIds(@Param("familyIds") List<UUID> familyIds);

    // Returns personal accounts belonging to a specific user
    @Query("SELECT a FROM Account a WHERE a.user.id = :userId AND a.deletedAt IS NULL")
    List<Account> findByUserId(@Param("userId") UUID userId);

    // Tenant-scoped lookup — only returns account if it belongs to one of caller's families or is owned by caller
    @Query("""
        SELECT a FROM Account a
        WHERE a.id = :id
          AND a.deletedAt IS NULL
          AND (a.family.id IN :familyIds OR a.user.id = :userId)
        """)
    Optional<Account> findByIdAndTenant(
            @Param("id") UUID id,
            @Param("familyIds") List<UUID> familyIds,
            @Param("userId") UUID userId
    );

    /**
     * Pull query for WatermelonDB sync.
     *
     * Returns all accounts (including soft-deleted) that were modified in the
     * half-open interval (since, until] and belong to the caller's tenant.
     *
     * Including deleted records is intentional — the caller uses deletedAt to
     * build the "deleted" list in the pull response.
     */
    @Query("""
        SELECT a FROM Account a
        WHERE a.updatedAt > :since
          AND a.updatedAt <= :until
          AND (a.family.id IN :familyIds OR a.user.id = :userId)
        """)
    List<Account> findModifiedForSync(
            @Param("since") java.time.Instant since,
            @Param("until") java.time.Instant until,
            @Param("familyIds") List<UUID> familyIds,
            @Param("userId") UUID userId
    );

    @Query("SELECT a FROM Account a WHERE a.name = :name AND a.deletedAt IS NULL AND (a.family.id IN :familyIds OR a.user.id = :userId)")
    Optional<Account> findByNameAndTenant(
            @Param("name") String name,
            @Param("familyIds") List<UUID> familyIds,
            @Param("userId") UUID userId
    );
}
