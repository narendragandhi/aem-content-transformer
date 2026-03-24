package com.example.aemtransformer.service;

import com.example.aemtransformer.model.MigrationLedgerEntry.MigrationStatus;
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
import java.util.Objects;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Batch processor with Idempotent Ledger (Bead 11) and SEO Redirect Chronicle (Bead 13).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchMigrationService {

    private final TransformationService transformationService;
    private final MigrationManifestStore manifestStore;
    private final AemValidationService validationService;
    private final MigrationReportService reportService;
    private final MigrationLedgerService ledgerService;
    private final SeoRedirectService seoRedirectService;

    @Value("${migration.batch.concurrency:4}")
    private int concurrency;

    @Value("${migration.batch.retry-failed:true}")
    private boolean retryFailed;

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

    public BatchResult runBatch(String sourceUrl, String contentType, int perPage, int maxPages) {
        long batchStart = System.currentTimeMillis();
        
        validationService.checkHealth();
        validationService.validatePath("template", templatePath);
        validationService.validatePath("fragment-model", fragmentModel);
        validationService.validatePath("tag-root", tagRoot);

        ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, concurrency));
        CompletionService<MigrationManifestEntry> completionService = new ExecutorCompletionService<>(executor);

        int page = checkpointEnabled ? loadCheckpoint() : 1;
        int startPage = page;
        
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
                    break;
                }

                int submitted = 0;
                for (WordPressContent content : contents) {
                    totalProcessed++;
                    
                    if (!ledgerService.shouldMigrate(content)) {
                        skippedCount++;
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

        if (checkpointEnabled) saveCheckpoint(page);

        // PRIME TIME: Generate SEO Redirect Configs (Bead 13)
        seoRedirectService.writeRedirectConfigs();

        BatchResult result = new BatchResult(totalProcessed, successCount, failureCount, skippedCount);
        reportService.writeReport(result, manifestStore.getManifestPath(), manifestStore.getDlqPath(), 
                                System.currentTimeMillis() - batchStart);
        return result;
    }

    private MigrationManifestEntry processContent(String sourceUrl, String contentType, WordPressContent content) {
        long start = System.currentTimeMillis();
        String key = buildKey(sourceUrl, contentType, content);

        try {
            TransformationResult result = transformationService.transformById(sourceUrl, content.getId(), contentType);
            long duration = System.currentTimeMillis() - start;
            double trustScore = (result.finalState() != null) ? result.finalState().getTrustScore() : 0.0;

            if (result.success()) {
                ledgerService.recordOutcome(content, result.outputPath(), MigrationStatus.SUCCESS, trustScore);
                
                // PRIME TIME: Record Redirect for SEO Chronicle (Bead 13)
                seoRedirectService.recordRedirect(content.getLink(), content.getSlug());
                
                return new MigrationManifestEntry(
                        key, sourceUrl, contentType, content.getId(), content.getSlug(),
                        valueOrNull(content.getModifiedDate()), MigrationManifestEntry.Status.SUCCESS,
                        result.outputPath(), null, 1, duration, Instant.now()
                );
            }

            ledgerService.recordOutcome(content, null, MigrationStatus.FAILED, trustScore);
            return new MigrationManifestEntry(
                    key, sourceUrl, contentType, content.getId(), content.getSlug(),
                    valueOrNull(content.getModifiedDate()), MigrationManifestEntry.Status.FAILED,
                    null, result.errorMessage(), 1, duration, Instant.now()
            );
        } catch (Exception e) {
            ledgerService.recordOutcome(content, null, MigrationStatus.FAILED, 0.0);
            return new MigrationManifestEntry(
                    key, sourceUrl, contentType, content.getId(), content.getSlug(),
                    valueOrNull(content.getModifiedDate()), MigrationManifestEntry.Status.FAILED,
                    null, e.getMessage(), 1, System.currentTimeMillis() - start, Instant.now()
            );
        }
    }

    private String buildKey(String sourceUrl, String contentType, WordPressContent content) {
        return String.join("|",
                Objects.toString(sourceUrl, ""),
                Objects.toString(contentType, ""),
                Objects.toString(content.getId(), "")
        );
    }

    private String valueOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    private void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private int loadCheckpoint() {
        try {
            Path path = Path.of(checkpointPath);
            if (!Files.exists(path)) return 1;
            String raw = Files.readString(path).trim();
            return raw.isBlank() ? 1 : Integer.parseInt(raw);
        } catch (Exception e) {
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
        if (raw == null || raw.isBlank()) return List.of();
        List<String> items = new java.util.ArrayList<>();
        for (String part : raw.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) items.add(value);
        }
        return items;
    }

    public record BatchResult(int totalProcessed, int successCount, int failureCount, int skippedCount) {}
}
