package com.family.finance.repository;

import com.family.finance.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // Returns transactions for accounts belonging to any of the given family IDs
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.account.family.id IN :familyIds
          AND t.deletedAt IS NULL
        """)
    List<Transaction> findByFamilyIds(@Param("familyIds") List<UUID> familyIds);

    // Returns transactions for personal accounts belonging to a specific user
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.account.user.id = :userId
          AND t.deletedAt IS NULL
        """)
    List<Transaction> findByUserId(@Param("userId") UUID userId);

    // Returns transactions for a specific account
    @Query("SELECT t FROM Transaction t WHERE t.account.id = :accountId AND t.deletedAt IS NULL")
    List<Transaction> findByAccountId(@Param("accountId") UUID accountId);

    // Tenant-scoped lookup — only returns transaction if its account belongs to tenant
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.id = :id
          AND t.deletedAt IS NULL
          AND (t.account.family.id IN :familyIds OR t.account.user.id = :userId)
        """)
    Optional<Transaction> findByIdAndTenant(
            @Param("id") UUID id,
            @Param("familyIds") List<UUID> familyIds,
            @Param("userId") UUID userId
    );

    /**
     * Pull query for WatermelonDB sync.
     * Returns all transactions (including soft-deleted) modified in (since, until]
     * for the caller's tenant (via account ownership).
     */
    @Query("""
        SELECT t FROM Transaction t
        WHERE t.updatedAt > :since
          AND t.updatedAt <= :until
          AND (t.account.family.id IN :familyIds OR t.account.user.id = :userId)
        """)
    List<Transaction> findModifiedForSync(
            @Param("since") Instant since,
            @Param("until") Instant until,
            @Param("familyIds") List<UUID> familyIds,
            @Param("userId") UUID userId
    );

    @Query("SELECT t FROM Transaction t WHERE t.importHash = :hash AND t.deletedAt IS NULL " +
           "AND (t.account.family.id IN :familyIds OR t.account.user.id = :userId)")
    Optional<Transaction> findByImportHash(
            @Param("hash") String importHash,
            @Param("familyIds") List<UUID> familyIds,
            @Param("userId") UUID userId
    );
}
