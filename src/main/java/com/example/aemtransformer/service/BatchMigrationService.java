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
    private final AemValidationService validationService;
    private final MigrationReportService reportService;

    @Value("${migration.batch.concurrency:4}")
    private int concurrency;

    @Value("${migration.batch.retry-failed:true}")
    private boolean retryFailed;

    @Value("${migration.batch.skip-output-check:false}")
    private boolean skipOutputCheck;

    @Value("${aem.template-path:/conf/mysite/settings/wcm/templates/content-page}")
    private String templatePath;

    @Value("${aem.fragments.content-model:/conf/mysite/settings/dam/cfm/models/article}")
    private String fragmentModel;

    @Value("${aem.tags.root:/content/cq:tags/mysite}")
    private String tagRoot;

    @Value("${aem.validation.extra-paths:}")
    private String extraValidationPaths;

    @Value("${migration.batch.checkpoint.enabled:true}")
    private boolean checkpointEnabled;

    @Value("${migration.batch.checkpoint.interval:100}")
    private int checkpointInterval;

    @Value("${migration.batch.checkpoint.path:./output/checkpoint.json}")
    private String checkpointPath;

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
        long batchStart = System.currentTimeMillis();
        validationService.checkHealth();
        validationService.validatePath("template", templatePath);
        validationService.validatePath("fragment-model", fragmentModel);
        validationService.validatePath("tag-root", tagRoot);
        for (String path : parseCsv(extraValidationPaths)) {
            validationService.validatePath("extra", path);
        }

        Map<String, MigrationManifestEntry> existing = manifestStore.loadLatestEntries();
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, concurrency));
        CompletionService<MigrationManifestEntry> completionService = new ExecutorCompletionService<>(executor);

        int page = 1;
        int startPage = 1;
        if (checkpointEnabled) {
            startPage = loadCheckpoint();
            page = Math.max(1, startPage);
        }
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
                            && (skipOutputCheck
                                || (previous.outputPath() != null && Files.exists(Path.of(previous.outputPath()))))) {
                        skippedCount++;
                        manifestStore.append(previous);
                        continue;
                    }

                    if (previous != null && previous.status() == MigrationManifestEntry.Status.FAILED && !retryFailed) {
                        skippedCount++;
                        manifestStore.append(previous);
                        continue;
                    }

                    int attempt = previous != null ? previous.attempts() + 1 : 1;
                    submitted++;
                    completionService.submit(() -> processContent(sourceUrl, contentType, content, attempt));
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
                            manifestStore.appendDlq(entry);
                        }
                    } catch (Exception e) {
                        failureCount++;
                        log.error("Batch task failed: {}", e.getMessage());
                    }
                }

                page++;
                if (checkpointEnabled && (page - startPage) % Math.max(1, checkpointInterval) == 0) {
                    saveCheckpoint(page);
                }
            }
        } finally {
            shutdownExecutor(executor);
        }

        if (checkpointEnabled) {
            saveCheckpoint(page);
        }
        BatchResult result = new BatchResult(totalProcessed, successCount, failureCount, skippedCount);
        long durationMs = System.currentTimeMillis() - batchStart;
        reportService.writeReport(result, manifestStore.getManifestPath(), manifestStore.getDlqPath(), durationMs);
        return result;
    }

    private MigrationManifestEntry processContent(String sourceUrl, String contentType, WordPressContent content,
                                                  int attempt) {
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
                        attempt,
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
                    attempt,
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
                    attempt,
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

    private int loadCheckpoint() {
        try {
            Path path = Path.of(checkpointPath);
            if (!Files.exists(path)) {
                return 1;
            }
            String raw = Files.readString(path).trim();
            if (raw.isBlank()) {
                return 1;
            }
            return Integer.parseInt(raw);
        } catch (Exception e) {
            log.warn("Failed to load checkpoint; starting from page 1");
            return 1;
        }
    }

    private void saveCheckpoint(int page) {
        try {
            Path path = Path.of(checkpointPath);
            Files.createDirectories(path.getParent());
            Files.writeString(path, Integer.toString(page));
        } catch (Exception e) {
            log.warn("Failed to write checkpoint at page {}", page);
        }
    }

    private List<String> parseCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> items = new java.util.ArrayList<>();
        for (String part : raw.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                items.add(value);
            }
        }
        return items;
    }

    public record BatchResult(int totalProcessed, int successCount, int failureCount, int skippedCount) {}
}
