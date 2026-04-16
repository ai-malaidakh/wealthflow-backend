package com.family.finance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "family_invites")
@Getter
@Setter
@NoArgsConstructor
public class FamilyInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false, unique = true, length = 12)
    private String code;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column
    private Instant usedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by")
    private User usedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean isValid() {
        return usedAt == null && Instant.now().isBefore(expiresAt);
    }
}
