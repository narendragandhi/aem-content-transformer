package com.example.aemtransformer.repository;

import com.example.aemtransformer.model.AssetLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for global asset deduplication checks.
 */
@Repository
public interface AssetLedgerRepository extends JpaRepository<AssetLedgerEntry, Long> {
    Optional<AssetLedgerEntry> findByChecksum(String checksum);
}
