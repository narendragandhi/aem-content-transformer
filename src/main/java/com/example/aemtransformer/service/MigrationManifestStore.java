package com.example.aemtransformer.service;

import com.example.aemtransformer.model.MigrationManifestEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Append-only JSONL manifest for batch migrations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationManifestStore {

    private final ObjectMapper objectMapper;

    @Value("${aem.output-path:./output}")
    private String outputPath;

    @Value("${migration.manifest.filename:manifest.jsonl}")
    private String manifestFilename;

    /**
     * Loads the latest entry for each content key.
     */
    public Map<String, MigrationManifestEntry> loadLatestEntries() {
        Map<String, MigrationManifestEntry> entries = new HashMap<>();
        Path manifest = getManifestPath();

        if (!Files.exists(manifest)) {
            return entries;
        }

        try (BufferedReader reader = Files.newBufferedReader(manifest)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                MigrationManifestEntry entry = objectMapper.readValue(line, MigrationManifestEntry.class);
                entries.put(entry.key(), entry);
            }
        } catch (IOException e) {
            log.warn("Failed to read manifest {}: {}", manifest, e.getMessage());
        }

        return entries;
    }

    /**
     * Appends a single entry to the manifest.
     */
    public void append(MigrationManifestEntry entry) {
        Path manifest = getManifestPath();
        try {
            Files.createDirectories(manifest.getParent());
            String line = objectMapper.writeValueAsString(entry) + System.lineSeparator();
            Files.writeString(manifest, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Failed to append manifest entry: {}", e.getMessage());
        }
    }

    public Path getManifestPath() {
        return Path.of(outputPath, manifestFilename);
    }
}
