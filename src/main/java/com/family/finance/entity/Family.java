package com.family.finance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "families")
@Getter
@Setter
@NoArgsConstructor
public class Family {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Column
    private Instant deletedAt;

    @Column(nullable = false)
    private long version = 0;

    @OneToMany(mappedBy = "family", fetch = FetchType.LAZY)
    private List<FamilyMember> members;

    @OneToMany(mappedBy = "family", fetch = FetchType.LAZY)
    private List<Account> accounts;

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        version++;
    }
}
