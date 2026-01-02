package com.example.aemtransformer.agent;

import com.example.aemtransformer.model.WordPressContent;
import com.example.aemtransformer.service.WordPressApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent responsible for scraping content from WordPress REST API.
 * In Embabel framework, this would be annotated with @Agent and @Action.
 * For simplicity, implemented as a Spring component that can be invoked by the workflow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WordPressScraperAgent {

    private final WordPressApiService wordPressApiService;

    /**
     * Scrapes a single post from WordPress.
     */
    public WordPressContent scrapePost(String baseUrl, Long postId) {
        log.info("Scraping WordPress post: {} from {}", postId, baseUrl);
        return wordPressApiService.fetchPost(baseUrl, postId);
    }

    /**
     * Scrapes a single page from WordPress.
     */
    public WordPressContent scrapePage(String baseUrl, Long pageId) {
        log.info("Scraping WordPress page: {} from {}", pageId, baseUrl);
        return wordPressApiService.fetchPage(baseUrl, pageId);
    }

    /**
     * Scrapes content by slug.
     */
    public WordPressContent scrapeBySlug(String baseUrl, String slug, String type) {
        log.info("Scraping WordPress content by slug: {} (type: {}) from {}", slug, type, baseUrl);
        return wordPressApiService.fetchBySlug(baseUrl, slug, type);
    }

    /**
     * Scrapes all posts with pagination.
     */
    public List<WordPressContent> scrapeAllPosts(String baseUrl, int page, int perPage) {
        log.info("Scraping all WordPress posts: page={}, perPage={}", page, perPage);
        return wordPressApiService.fetchAllPosts(baseUrl, page, perPage);
    }

    /**
     * Scrapes all pages with pagination.
     */
    public List<WordPressContent> scrapeAllPages(String baseUrl, int page, int perPage) {
        log.info("Scraping all WordPress pages: page={}, perPage={}", page, perPage);
        return wordPressApiService.fetchAllPages(baseUrl, page, perPage);
    }
}
