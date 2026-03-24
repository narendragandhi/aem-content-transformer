package com.example.aemtransformer.repository;

import com.example.aemtransformer.model.MigrationLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for interacting with the Migration Ledger Database.
 */
@Repository
public interface MigrationLedgerRepository extends JpaRepository<MigrationLedgerEntry, Long> {
    Optional<MigrationLedgerEntry> findBySlug(String slug);
}
