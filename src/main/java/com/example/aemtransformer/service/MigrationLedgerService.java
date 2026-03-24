package com.example.aemtransformer.service;

import com.example.aemtransformer.model.MigrationLedgerEntry;
import com.example.aemtransformer.model.MigrationLedgerEntry.MigrationStatus;
import com.example.aemtransformer.model.WordPressContent;
import com.example.aemtransformer.repository.MigrationLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Service for the Idempotent Migration Ledger (Bead 11).
 * Ensures pages are only migrated if they are new or updated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationLedgerService {

    private final MigrationLedgerRepository repository;

    /**
     * Determines if a WordPress post should be migrated based on its status in the ledger.
     */
    @Transactional(readOnly = true)
    public boolean shouldMigrate(WordPressContent content) {
        if (content == null || content.getId() == null) {
            return true;
        }

        Optional<MigrationLedgerEntry> existing = repository.findById(content.getId());
        
        if (existing.isEmpty()) {
            log.info("Page {} (ID: {}) not found in ledger; migrating.", content.getSlug(), content.getId());
            return true;
        }

        MigrationLedgerEntry entry = existing.get();
        
        // Only skip if it was previously successful AND the source hasn't changed
        if (entry.getStatus() == MigrationStatus.SUCCESS) {
            Instant sourceModified = content.getModified();
            if (sourceModified != null && entry.getLastModified() != null) {
                if (sourceModified.isAfter(entry.getLastModified())) {
                    log.info("Page {} has been updated since last migration; re-migrating.", content.getSlug());
                    return true;
                }
            }
            log.info("Page {} already successfully migrated; skipping.", content.getSlug());
            return false;
        }

        log.info("Previous migration for {} was {} or missing; retrying.", content.getSlug(), entry.getStatus());
        return true;
    }

    /**
     * Records the outcome of a migration attempt.
     */
    @Transactional
    public void recordOutcome(WordPressContent content, String aemPath, MigrationStatus status, double trustScore) {
        if (content == null || content.getId() == null) return;

        MigrationLedgerEntry entry = MigrationLedgerEntry.builder()
                .wpId(content.getId())
                .slug(content.getSlug())
                .lastModified(content.getModified())
                .aemPath(aemPath)
                .status(status)
                .trustScore(trustScore)
                .migratedAt(Instant.now())
                .build();

        repository.save(entry);
        log.info("Recorded outcome for {}: {} (Score: {})", content.getSlug(), status, trustScore);
    }
}
