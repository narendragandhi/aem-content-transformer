package com.example.aemtransformer.model;

import java.time.Instant;

/**
 * Single entry in the migration manifest for batch processing.
 *
 * @param key unique key for the content item
 * @param sourceUrl WordPress base URL
 * @param contentType content type (post/page)
 * @param contentId WordPress content ID
 * @param slug WordPress slug
 * @param modifiedDate WordPress modified date as string
 * @param status migration status
 * @param outputPath output path if successful
 * @param errorMessage error message if failed
 * @param attempts number of attempts
 * @param durationMs duration in milliseconds
 * @param timestamp time the entry was recorded
 */
public record MigrationManifestEntry(
        String key,
        String sourceUrl,
        String contentType,
        Long contentId,
        String slug,
        String modifiedDate,
        Status status,
        String outputPath,
        String errorMessage,
        int attempts,
        long durationMs,
        Instant timestamp
) {
    public enum Status {
        SUCCESS,
        FAILED,
        SKIPPED
    }
}
