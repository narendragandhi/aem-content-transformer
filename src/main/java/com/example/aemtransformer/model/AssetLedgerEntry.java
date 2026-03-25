package com.example.aemtransformer.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Entity representing a unique asset in the DAM to prevent duplicates.
 */
@Entity
@Table(name = "asset_ledger", indexes = {
    @Index(name = "idx_asset_hash", columnList = "checksum")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String checksum; // SHA-256 hash of the binary

    @Column(name = "dam_path", nullable = false)
    private String damPath; // The first AEM DAM path assigned to this hash

    @Column(name = "source_url", length = 1024)
    private String sourceUrl;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "ingested_at")
    private Instant ingestedAt;
}
