package com.family.finance.controller;

import com.family.finance.dto.family.*;
import com.family.finance.entity.FamilyMember;
import com.family.finance.service.FamilyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/families")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;

    /**
     * Create an invite code for a family. Admin only. Code expires in 7 days.
     */
    @PostMapping("/{familyId}/invites")
    public ResponseEntity<CreateInviteResponse> createInvite(@PathVariable UUID familyId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(familyService.createInvite(familyId));
    }

    /**
     * Join a family using an invite code.
     */
    @PostMapping("/join")
    public ResponseEntity<JoinFamilyResponse> joinFamily(@Valid @RequestBody JoinFamilyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(familyService.joinFamily(request.code()));
    }

    /**
     * List active members of a family. Caller must be a member.
     */
    @GetMapping("/{familyId}/members")
    public List<FamilyMemberDto> listMembers(@PathVariable UUID familyId) {
        return familyService.listMembers(familyId);
    }

    /**
     * Update a member's role. Admin only.
     */
    @PatchMapping("/{familyId}/members/{memberId}/role")
    public FamilyMemberDto updateMemberRole(
            @PathVariable UUID familyId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateMemberRoleRequest request) {
        return familyService.updateMemberRole(familyId, memberId, request.role());
    }

    /**
     * Remove a member from a family. Admin only. Cannot remove self or last admin.
     */
    @DeleteMapping("/{familyId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID familyId, @PathVariable UUID memberId) {
        familyService.removeMember(familyId, memberId);
    }
}
