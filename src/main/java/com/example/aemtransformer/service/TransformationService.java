package com.example.aemtransformer.service;

import com.example.aemtransformer.agent.AemGeneratorAgent;
import com.example.aemtransformer.agent.ComponentMapperAgent;
import com.example.aemtransformer.agent.ContentAnalyzerAgent;
import com.example.aemtransformer.agent.WordPressScraperAgent;
import com.example.aemtransformer.model.AemPage;
import com.example.aemtransformer.model.ComponentMapping;
import com.example.aemtransformer.model.ContentAnalysis;
import com.example.aemtransformer.model.WordPressContent;
import com.example.aemtransformer.util.ParsedUrl;
import com.example.aemtransformer.workflow.TransformationWorkflow;
import com.example.aemtransformer.workflow.TransformationWorkflow.TransformationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Service that coordinates end-to-end transformations across entry points.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransformationService {

    private final WordPressScraperAgent wordPressScraperAgent;
    private final ContentAnalyzerAgent contentAnalyzerAgent;
    private final ComponentMapperAgent componentMapperAgent;
    private final AemGeneratorAgent aemGeneratorAgent;
    private final AemOutputService aemOutputService;
    private final TransformationWorkflow transformationWorkflow;

    /**
     * Transforms a WordPress URL into an AEM page JSON file.
     *
     * @param url WordPress article or page URL
     * @return path to the generated JSON file
     * @throws IOException if writing the output fails
     */
    public Path transformByUrl(String url) throws IOException {
        ParsedUrl parsed = ParsedUrl.parse(url);
        WordPressContent wpContent = wordPressScraperAgent.scrapeBySlug(parsed.baseUrl, parsed.slug, parsed.type);
        ContentAnalysis analysis = contentAnalyzerAgent.analyze(wpContent);
        List<ComponentMapping> mappings = componentMapperAgent.mapComponents(analysis);
        AemPage page = aemGeneratorAgent.generate(analysis, mappings);
        return aemOutputService.writePage(page, parsed.slug);
    }

    /**
     * Transforms a WordPress item by ID using the workflow graph.
     *
     * @param sourceUrl WordPress base URL
     * @param contentId WordPress content ID
     * @param contentType content type (post/page)
     * @return workflow result
     */
    public TransformationResult transformById(String sourceUrl, Long contentId, String contentType) {
        log.info("Starting transformation of {} {} from {}", contentType, contentId, sourceUrl);
        return transformationWorkflow.execute(sourceUrl, contentId, contentType);
    }

    /**
     * Fetches all posts from a WordPress site with pagination.
     *
     * @param sourceUrl WordPress base URL
     * @param page page number
     * @param perPage items per page
     * @return list of posts for the requested page
     */
    public List<WordPressContent> fetchAllPosts(String sourceUrl, int page, int perPage) {
        return transformationWorkflow.fetchAllPosts(sourceUrl, page, perPage);
    }

    /**
     * Fetches all pages from a WordPress site with pagination.
     *
     * @param sourceUrl WordPress base URL
     * @param page page number
     * @param perPage items per page
     * @return list of pages for the requested page
     */
    public List<WordPressContent> fetchAllPages(String sourceUrl, int page, int perPage) {
        return transformationWorkflow.fetchAllPages(sourceUrl, page, perPage);
    }
}
