package com.family.finance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category {

    public enum CategoryType {
        INCOME, EXPENSE
    }

    @Id
    private UUID id;

    // Ownership: exactly one of family_id or user_id must be set (enforced by CHECK constraint)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private Family family;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryType type;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Column
    private Instant deletedAt;

    @Column(nullable = false)
    private long version = 0;

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        version++;
    }
}
