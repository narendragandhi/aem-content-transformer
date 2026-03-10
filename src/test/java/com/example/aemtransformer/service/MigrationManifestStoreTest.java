package com.example.aemtransformer.service;

import com.example.aemtransformer.config.AppConfig;
import com.example.aemtransformer.model.MigrationManifestEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {AppConfig.class, MigrationManifestStore.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigrationManifestStoreTest {

    private static final Path OUTPUT_DIR = createTempDir();

    @Autowired
    private MigrationManifestStore manifestStore;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("aem.output-path", () -> OUTPUT_DIR.toString());
        registry.add("migration.manifest.filename", () -> "test-manifest.jsonl");
    }

    @Test
    void appendAndLoadLatestEntries_roundTripsEntry() {
        MigrationManifestEntry entry = new MigrationManifestEntry(
                "key-1",
                "https://example.com",
                "post",
                123L,
                "slug",
                "2026-03-10T10:00:00",
                MigrationManifestEntry.Status.SUCCESS,
                "/tmp/output.json",
                null,
                1,
                50,
                Instant.now()
        );

        manifestStore.append(entry);
        Map<String, MigrationManifestEntry> latest = manifestStore.loadLatestEntries();

        assertTrue(latest.containsKey("key-1"));
        assertEquals(MigrationManifestEntry.Status.SUCCESS, latest.get("key-1").status());
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("aem-manifest-test-");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create temp output directory", e);
        }
    }
}
