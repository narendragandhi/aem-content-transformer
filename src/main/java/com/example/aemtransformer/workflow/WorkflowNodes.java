package com.example.aemtransformer.workflow;

import com.example.aemtransformer.agent.AemGeneratorAgent;
import com.example.aemtransformer.agent.ComponentMapperAgent;
import com.example.aemtransformer.agent.ContentAnalyzerAgent;
import com.example.aemtransformer.agent.WordPressScraperAgent;
import com.example.aemtransformer.components.ExperienceFragmentComponent;
import com.example.aemtransformer.model.AemPage;
import com.example.aemtransformer.model.ComponentMapping;
import com.example.aemtransformer.model.ContentAnalysis;
import com.example.aemtransformer.model.TagMapping;
import com.example.aemtransformer.model.WordPressContent;
import com.example.aemtransformer.service.AemOutputService;
import com.example.aemtransformer.service.ContentFragmentService;
import com.example.aemtransformer.service.ExperienceFragmentService;
import com.example.aemtransformer.service.ParityReportService;
import com.example.aemtransformer.service.TagMappingService;
import com.example.aemtransformer.service.TagOutputService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.aemtransformer.workflow.TransformationState.*;

/**
 * Node implementations for the LangGraph4j workflow.
 * Each node performs a specific step in the transformation pipeline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowNodes {

    private final WordPressScraperAgent scraperAgent;
    private final ContentAnalyzerAgent analyzerAgent;
    private final ComponentMapperAgent mapperAgent;
    private final AemGeneratorAgent generatorAgent;
    private final AemOutputService outputService;
    private final TagMappingService tagMappingService;
    private final TagOutputService tagOutputService;
    private final ContentFragmentService contentFragmentService;
    private final ExperienceFragmentService experienceFragmentService;
    private final ParityReportService parityReportService;

    /**
     * Scrapes content from WordPress.
     */
    public Map<String, Object> scrapeNode(TransformationState state) {
        log.info("=== SCRAPE NODE ===");
        Map<String, Object> updates = new HashMap<>();

        try {
            String sourceUrl = state.getSourceUrl();
            Long contentId = state.getContentId();
            String contentType = normalizeContentType(state.getContentType());

            WordPressContent content;
            if (contentId != null) {
                content = "page".equals(contentType)
                        ? scraperAgent.scrapePage(sourceUrl, contentId)
                        : scraperAgent.scrapePost(sourceUrl, contentId);
            } else {
                throw new IllegalStateException("Content ID is required for scraping");
            }

            updates.put(WORDPRESS_CONTENT_KEY, content);
            updates.put(CURRENT_PHASE_KEY, "scraped");
            updates.put(ERRORS_KEY, new ArrayList<>());
            updates.put(RETRY_COUNT_KEY, 0);
            log.info("Successfully scraped content: {}", content.getTitleText());

        } catch (Exception e) {
            log.error("Scrape failed", e);
            List<String> errors = new ArrayList<>(state.getErrors());
            errors.add("Scrape error: " + e.getMessage());
            updates.put(ERRORS_KEY, errors);
            updates.put(RETRY_COUNT_KEY, state.getRetryCount() + 1);
            updates.put(CURRENT_PHASE_KEY, "error");
        }

        return updates;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        String normalized = contentType.trim().toLowerCase();
        if ("pages".equals(normalized)) {
            return "page";
        }
        if ("posts".equals(normalized)) {
            return "post";
        }
        return normalized;
    }

    /**
     * Analyzes the scraped content structure.
     */
    public Map<String, Object> analyzeNode(TransformationState state) {
        log.info("=== ANALYZE NODE ===");
        Map<String, Object> updates = new HashMap<>();

        try {
            WordPressContent content = state.getWordPressContent();
            if (content == null) {
                throw new IllegalStateException("No WordPress content available for analysis");
            }

            ContentAnalysis analysis = analyzerAgent.analyze(content);
            updates.put(CONTENT_ANALYSIS_KEY, analysis);
            updates.put(CURRENT_PHASE_KEY, "analyzed");
            updates.put(ERRORS_KEY, new ArrayList<>());
            updates.put(RETRY_COUNT_KEY, 0);
            log.info("Successfully analyzed content: {} blocks found", analysis.getBlocks().size());

        } catch (Exception e) {
            log.error("Analysis failed", e);
            List<String> errors = new ArrayList<>(state.getErrors());
            errors.add("Analysis error: " + e.getMessage());
            updates.put(ERRORS_KEY, errors);
            updates.put(RETRY_COUNT_KEY, state.getRetryCount() + 1);
            updates.put(CURRENT_PHASE_KEY, "error");
        }

        return updates;
    }

    /**
     * Maps content blocks to AEM components.
     */
    public Map<String, Object> transformNode(TransformationState state) {
        log.info("=== TRANSFORM NODE ===");
        Map<String, Object> updates = new HashMap<>();

        try {
            ContentAnalysis analysis = state.getContentAnalysis();
            if (analysis == null) {
                throw new IllegalStateException("No content analysis available for transformation");
            }

            List<ComponentMapping> mappings = mapperAgent.mapComponents(analysis);
            updates.put(COMPONENT_MAPPINGS_KEY, mappings);
            updates.put(CURRENT_PHASE_KEY, "transformed");
            updates.put(ERRORS_KEY, new ArrayList<>());
            updates.put(RETRY_COUNT_KEY, 0);
            log.info("Successfully mapped {} components", mappings.size());

        } catch (Exception e) {
            log.error("Transform failed", e);
            List<String> errors = new ArrayList<>(state.getErrors());
            errors.add("Transform error: " + e.getMessage());
            updates.put(ERRORS_KEY, errors);
            updates.put(RETRY_COUNT_KEY, state.getRetryCount() + 1);
            updates.put(CURRENT_PHASE_KEY, "error");
        }

        return updates;
    }

    /**
     * Generates the AEM page structure.
     */
    public Map<String, Object> generateNode(TransformationState state) {
        log.info("=== GENERATE NODE ===");
        Map<String, Object> updates = new HashMap<>();

        try {
            ContentAnalysis analysis = state.getContentAnalysis();
            List<ComponentMapping> mappings = state.getComponentMappings();
            WordPressContent content = state.getWordPressContent();

            if (analysis == null || mappings == null || mappings.isEmpty()) {
                throw new IllegalStateException("Missing analysis or mappings for generation");
            }

            AemPage page = generatorAgent.generate(analysis, mappings);
            List<TagMapping> tagMappings = tagMappingService.mapTags(state.getSourceUrl(),
                    content != null ? content.getTags() : List.of());
            if (!tagMappings.isEmpty() && page.getContent() != null) {
                String[] tagPaths = tagMappings.stream().map(TagMapping::path).toArray(String[]::new);
                page.getContent().setTags(tagPaths);
            }

            if (experienceFragmentService.isEmbedOnPage()
                    && page.getContent() != null
                    && page.getContent().getRoot() != null) {
                String xfPath = experienceFragmentService.buildXfPath(
                        content != null ? content.getSlug() : "fragment");
                ExperienceFragmentComponent xfComponent = ExperienceFragmentComponent.builder()
                        .componentName("experiencefragment_migrated")
                        .fragmentPath(xfPath)
                        .build();
                page.getContent().getRoot().addChild("experiencefragment_migrated", xfComponent.toComponentNode());
            }
            updates.put(AEM_PAGE_KEY, page);
            updates.put(TAG_MAPPINGS_KEY, tagMappings);
            updates.put(CURRENT_PHASE_KEY, "generated");
            updates.put(ERRORS_KEY, new ArrayList<>());
            updates.put(RETRY_COUNT_KEY, 0);
            log.info("Successfully generated AEM page");

        } catch (Exception e) {
            log.error("Generate failed", e);
            List<String> errors = new ArrayList<>(state.getErrors());
            errors.add("Generate error: " + e.getMessage());
            updates.put(ERRORS_KEY, errors);
            updates.put(RETRY_COUNT_KEY, state.getRetryCount() + 1);
            updates.put(CURRENT_PHASE_KEY, "error");
        }

        return updates;
    }

    /**
     * Writes the generated AEM page to the file system.
     */
    public Map<String, Object> outputNode(TransformationState state) {
        log.info("=== OUTPUT NODE ===");
        Map<String, Object> updates = new HashMap<>();

        try {
            AemPage page = state.getAemPage();
            WordPressContent content = state.getWordPressContent();
            List<TagMapping> tagMappings = state.getTagMappings();

            if (page == null) {
                throw new IllegalStateException("No AEM page available for output");
            }

            String pageName = content != null ? content.getSlug() : "page";
            AemOutputService.PackageWriteResult result = outputService.writePagePackageWithResult(page, pageName);
            Path outputPath = result.contentPath();
            if (result.fileVaultZip() != null) {
                outputPath = result.fileVaultZip();
            }
            if (outputService.isPackageZipEnabled()) {
                outputPath = outputService.zipPackage(result.packageRoot());
            }

            if (tagMappings != null && !tagMappings.isEmpty()) {
                tagOutputService.writeTags(result.packageRoot(), tagMappings);
            }

            String fragmentPath = null;
            if (contentFragmentService.isEnabled()) {
                List<String> tagPaths = tagMappings == null ? List.of()
                        : tagMappings.stream().map(TagMapping::path).toList();
                fragmentPath = contentFragmentService.writeContentFragment(result.packageRoot(), content, tagPaths);
            }

            if (experienceFragmentService.isEnabled()) {
                String xfPath = experienceFragmentService.writeExperienceFragment(
                        result.packageRoot(),
                        content != null ? content.getSlug() : "fragment",
                        content != null ? content.getTitleText() : "Fragment",
                        fragmentPath
                );
                if (xfPath != null && outputService.isPackageZipEnabled()) {
                    outputPath = outputService.zipPackage(result.packageRoot());
                }
            }

            double trustScore = parityReportService.append(content, state.getContentAnalysis(), page, outputPath.toString());

            updates.put(OUTPUT_PATH_KEY, outputPath.toString());
            updates.put(TRUST_SCORE_KEY, trustScore);
            updates.put(CURRENT_PHASE_KEY, "completed");
            updates.put(ERRORS_KEY, new ArrayList<>());
            updates.put(RETRY_COUNT_KEY, 0);
            log.info("Successfully wrote AEM page to: {}", outputPath);

        } catch (Exception e) {
            log.error("Output failed", e);
            List<String> errors = new ArrayList<>(state.getErrors());
            errors.add("Output error: " + e.getMessage());
            updates.put(ERRORS_KEY, errors);
            updates.put(RETRY_COUNT_KEY, state.getRetryCount() + 1);
            updates.put(CURRENT_PHASE_KEY, "error");
        }

        return updates;
    }

    /**
     * Determines the next step based on current state.
     */
    public String routeAfterGenerate(TransformationState state) {
        if (state.hasErrors()) {
            int retryCount = state.getRetryCount();
            if (retryCount < 3) {
                log.info("Errors detected, will retry (attempt {})", retryCount + 1);
                return "retry";
            } else {
                log.error("Max retries exceeded");
                return "fail";
            }
        }
        return "success";
    }
}
