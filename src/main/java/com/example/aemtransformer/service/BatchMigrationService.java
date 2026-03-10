package com.example.aemtransformer.service;

import com.example.aemtransformer.model.MigrationManifestEntry;
import com.example.aemtransformer.model.WordPressContent;
import com.example.aemtransformer.workflow.TransformationWorkflow.TransformationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Batch processor with checkpointing and bounded concurrency.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchMigrationService {

    private final TransformationService transformationService;
    private final MigrationManifestStore manifestStore;

    @Value("${migration.batch.concurrency:4}")
    private int concurrency;

    /**
     * Runs a batch migration with checkpointing.
     *
     * @param sourceUrl WordPress base URL
     * @param contentType content type (post/page)
     * @param perPage page size
     * @param maxPages max pages to scan
     * @return batch result summary
     */
    public BatchResult runBatch(String sourceUrl, String contentType, int perPage, int maxPages) {
        Map<String, MigrationManifestEntry> existing = manifestStore.loadLatestEntries();
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, concurrency));
        CompletionService<MigrationManifestEntry> completionService = new ExecutorCompletionService<>(executor);

        int page = 1;
        int totalProcessed = 0;
        int successCount = 0;
        int failureCount = 0;
        int skippedCount = 0;

        try {
            while (page <= maxPages) {
                log.info("Processing page {} of {}...", page, contentType);

                List<WordPressContent> contents = "posts".equals(contentType) || "post".equals(contentType)
                        ? transformationService.fetchAllPosts(sourceUrl, page, perPage)
                        : transformationService.fetchAllPages(sourceUrl, page, perPage);

                if (contents.isEmpty()) {
                    log.info("No more {} found at page {}", contentType, page);
                    break;
                }

                int submitted = 0;
                for (WordPressContent content : contents) {
                    totalProcessed++;
                    String key = buildKey(sourceUrl, contentType, content);

                    MigrationManifestEntry previous = existing.get(key);
                    if (previous != null
                            && previous.status() == MigrationManifestEntry.Status.SUCCESS
                            && previous.outputPath() != null
                            && Files.exists(Path.of(previous.outputPath()))) {
                        skippedCount++;
                        manifestStore.append(previous);
                        continue;
                    }

                    submitted++;
                    completionService.submit(() -> processContent(sourceUrl, contentType, content));
                }

                for (int i = 0; i < submitted; i++) {
                    try {
                        MigrationManifestEntry entry = completionService.take().get();
                        manifestStore.append(entry);

                        if (entry.status() == MigrationManifestEntry.Status.SUCCESS) {
                            successCount++;
                        } else if (entry.status() == MigrationManifestEntry.Status.SKIPPED) {
                            skippedCount++;
                        } else {
                            failureCount++;
                        }
                    } catch (Exception e) {
                        failureCount++;
                        log.error("Batch task failed: {}", e.getMessage());
                    }
                }

                page++;
            }
        } finally {
            shutdownExecutor(executor);
        }

        return new BatchResult(totalProcessed, successCount, failureCount, skippedCount);
    }

    private MigrationManifestEntry processContent(String sourceUrl, String contentType, WordPressContent content) {
        long start = System.currentTimeMillis();
        String key = buildKey(sourceUrl, contentType, content);

        try {
            TransformationResult result = transformationService.transformById(sourceUrl, content.getId(), contentType);
            long duration = System.currentTimeMillis() - start;

            if (result.success()) {
                return new MigrationManifestEntry(
                        key,
                        sourceUrl,
                        contentType,
                        content.getId(),
                        content.getSlug(),
                        valueOrNull(content.getModifiedDate()),
                        MigrationManifestEntry.Status.SUCCESS,
                        result.outputPath(),
                        null,
                        1,
                        duration,
                        Instant.now()
                );
            }

            return new MigrationManifestEntry(
                    key,
                    sourceUrl,
                    contentType,
                    content.getId(),
                    content.getSlug(),
                    valueOrNull(content.getModifiedDate()),
                    MigrationManifestEntry.Status.FAILED,
                    null,
                    result.errorMessage(),
                    1,
                    duration,
                    Instant.now()
            );
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            return new MigrationManifestEntry(
                    key,
                    sourceUrl,
                    contentType,
                    content.getId(),
                    content.getSlug(),
                    valueOrNull(content.getModifiedDate()),
                    MigrationManifestEntry.Status.FAILED,
                    null,
                    e.getMessage(),
                    1,
                    duration,
                    Instant.now()
            );
        }
    }

    private String buildKey(String sourceUrl, String contentType, WordPressContent content) {
        String modified = valueOrNull(content.getModifiedDate());
        return String.join("|",
                Objects.toString(sourceUrl, ""),
                Objects.toString(contentType, ""),
                Objects.toString(content.getId(), ""),
                Objects.toString(modified, "")
        );
    }

    private String valueOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    private void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public record BatchResult(int totalProcessed, int successCount, int failureCount, int skippedCount) {}
}
