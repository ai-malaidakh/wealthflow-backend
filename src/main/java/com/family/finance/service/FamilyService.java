package com.family.finance.service;

import com.family.finance.dto.family.*;
import com.family.finance.entity.Family;
import com.family.finance.entity.FamilyInvite;
import com.family.finance.entity.FamilyMember;
import com.family.finance.entity.User;
import com.family.finance.repository.FamilyInviteRepository;
import com.family.finance.repository.FamilyMemberRepository;
import com.family.finance.repository.FamilyRepository;
import com.family.finance.repository.UserRepository;
import com.family.finance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FamilyService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RNG = new SecureRandom();

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyInviteRepository familyInviteRepository;
    private final UserRepository userRepository;

    // --- Helpers ---

    private Family requireMemberFamily(UUID familyId) {
        if (!TenantContext.getCurrentFamilyIds().contains(familyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this family");
        }
        return familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Family not found"));
    }

    private FamilyMember requireAdminMembership(UUID familyId) {
        FamilyMember membership = familyMemberRepository
                .findByFamilyIdAndUserId(familyId, TenantContext.getCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this family"));
        if (membership.getRole() != FamilyMember.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
        return membership;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(RNG.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private FamilyMemberDto toDto(FamilyMember fm) {
        User u = fm.getUser();
        return new FamilyMemberDto(
                fm.getId(),
                u.getId(),
                u.getEmail(),
                u.getDisplayName(),
                fm.getRole().name(),
                fm.getJoinedAt()
        );
    }

    // --- API operations ---

    @Transactional
    public CreateInviteResponse createInvite(UUID familyId) {
        requireMemberFamily(familyId);
        requireAdminMembership(familyId);

        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        User creator = userRepository.findById(TenantContext.getCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        FamilyInvite invite = new FamilyInvite();
        invite.setFamily(family);
        invite.setCreatedBy(creator);
        invite.setCode(generateCode());
        invite.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        invite = familyInviteRepository.save(invite);

        return new CreateInviteResponse(invite.getId(), invite.getCode(), invite.getExpiresAt());
    }

    @Transactional
    public JoinFamilyResponse joinFamily(String code) {
        UUID userId = TenantContext.getCurrentUserId();

        FamilyInvite invite = familyInviteRepository.findValidByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid or expired invite code"));

        UUID familyId = invite.getFamily().getId();

        // Already a member?
        familyMemberRepository.findByFamilyIdAndUserId(familyId, userId)
                .ifPresent(m -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Already a member of this family");
                });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        FamilyMember membership = new FamilyMember();
        membership.setFamily(invite.getFamily());
        membership.setUser(user);
        membership.setRole(FamilyMember.Role.MEMBER);
        familyMemberRepository.save(membership);

        // Consume the invite
        invite.setUsedAt(Instant.now());
        invite.setUsedBy(user);
        familyInviteRepository.save(invite);

        return new JoinFamilyResponse(
                familyId,
                invite.getFamily().getName(),
                FamilyMember.Role.MEMBER.name()
        );
    }

    @Transactional(readOnly = true)
    public List<FamilyMemberDto> listMembers(UUID familyId) {
        requireMemberFamily(familyId);
        return familyMemberRepository.findActiveByFamilyId(familyId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public FamilyMemberDto updateMemberRole(UUID familyId, UUID memberId, FamilyMember.Role newRole) {
        requireMemberFamily(familyId);
        requireAdminMembership(familyId);

        FamilyMember target = familyMemberRepository.findById(memberId)
                .filter(m -> m.getFamily().getId().equals(familyId) && m.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        target.setRole(newRole);
        return toDto(familyMemberRepository.save(target));
    }

    @Transactional
    public void removeMember(UUID familyId, UUID memberId) {
        requireMemberFamily(familyId);
        requireAdminMembership(familyId);

        FamilyMember target = familyMemberRepository.findById(memberId)
                .filter(m -> m.getFamily().getId().equals(familyId) && m.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        // Cannot remove self
        if (target.getUser().getId().equals(TenantContext.getCurrentUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot remove yourself from the family");
        }

        // Ensure at least one ADMIN remains
        long adminCount = familyMemberRepository.findActiveByFamilyId(familyId)
                .stream()
                .filter(m -> m.getRole() == FamilyMember.Role.ADMIN && !m.getId().equals(memberId))
                .count();
        if (adminCount == 0 && target.getRole() == FamilyMember.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot remove the last admin");
        }

        target.setDeletedAt(Instant.now());
        familyMemberRepository.save(target);
    }
}
