package com.example.aemtransformer.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Entity representing a single page's migration status in the Idempotent Ledger.
 */
@Entity
@Table(name = "migration_ledger")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationLedgerEntry {

    @Id
    @Column(name = "wp_id")
    private Long wpId;

    @Column(nullable = false)
    private String slug;

    @Column(name = "last_modified")
    private Instant lastModified;

    @Column(name = "aem_path")
    private String aemPath;

    @Enumerated(EnumType.STRING)
    private MigrationStatus status;

    @Column(name = "trust_score")
    private Double trustScore;

    @Column(name = "migrated_at")
    private Instant migratedAt;

    public enum MigrationStatus {
        PENDING,
        SUCCESS,
        FAILED,
        SKIPPED
    }
}
