package com.family.finance.repository;

import com.family.finance.entity.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, UUID> {

    @Query("SELECT fm FROM FamilyMember fm WHERE fm.user.id = :userId AND fm.deletedAt IS NULL")
    List<FamilyMember> findActiveByUserId(@Param("userId") UUID userId);

    @Query("SELECT fm FROM FamilyMember fm JOIN FETCH fm.user WHERE fm.family.id = :familyId AND fm.deletedAt IS NULL")
    List<FamilyMember> findActiveByFamilyId(@Param("familyId") UUID familyId);

    @Query("SELECT fm FROM FamilyMember fm WHERE fm.family.id = :familyId AND fm.user.id = :userId AND fm.deletedAt IS NULL")
    Optional<FamilyMember> findByFamilyIdAndUserId(@Param("familyId") UUID familyId, @Param("userId") UUID userId);
}
