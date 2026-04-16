package com.family.finance.repository;

import com.family.finance.entity.SyncConflict;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SyncConflictRepository extends JpaRepository<SyncConflict, UUID> {
}
