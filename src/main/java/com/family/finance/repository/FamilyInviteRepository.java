package com.family.finance.repository;

import com.family.finance.entity.FamilyInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FamilyInviteRepository extends JpaRepository<FamilyInvite, UUID> {

    @Query("SELECT i FROM FamilyInvite i WHERE i.code = :code AND i.usedAt IS NULL AND i.expiresAt > CURRENT_TIMESTAMP")
    Optional<FamilyInvite> findValidByCode(@Param("code") String code);
}
