package com.example.aemtransformer.service;

import com.example.aemtransformer.exception.ContentParsingException;
import com.example.aemtransformer.exception.WordPressApiException;
import com.example.aemtransformer.model.WordPressContent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Service for fetching content from WordPress REST API.
 */
@Slf4j
@Service
public class WordPressApiService {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final RateLimiterService rateLimiter;

    @Value("${wordpress.api-path:/wp-json/wp/v2}")
    private String apiPath;

    @Value("${wordpress.timeout.connect:5000}")
    private int connectTimeout;

    @Value("${wordpress.timeout.read:30000}")
    private int readTimeout;

    @Value("${wordpress.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${wordpress.retry.delay-ms:1000}")
    private int retryDelayMs;

    public WordPressApiService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
                               RateLimiterService rateLimiter) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
    }

    private RestClient createClient(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        return restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * Fetches a single post by ID from the WordPress site.
     */
    public WordPressContent fetchPost(String baseUrl, Long postId) {
        String fullUrl = buildUrl(baseUrl, "/posts/" + postId + "?_embed=true");
        log.info("Fetching WordPress post from: {}", fullUrl);

        RestClient client = createClient(baseUrl);
        String response = executeWithRetry(() -> client.get()
                .uri(uriBuilder -> uriBuilder
                        .path(apiPath + "/posts/" + postId)
                        .queryParam("_embed", true)
                        .build())
                .retrieve()
                .body(String.class), fullUrl);
        return parseResponse(response, WordPressContent.class, fullUrl);
    }

    /**
     * Fetches a single page by ID from the WordPress site.
     */
    public WordPressContent fetchPage(String baseUrl, Long pageId) {
        String fullUrl = buildUrl(baseUrl, "/pages/" + pageId + "?_embed=true");
        log.info("Fetching WordPress page from: {}", fullUrl);

        RestClient client = createClient(baseUrl);
        String response = executeWithRetry(() -> client.get()
                .uri(uriBuilder -> uriBuilder
                        .path(apiPath + "/pages/" + pageId)
                        .queryParam("_embed", true)
                        .build())
                .retrieve()
                .body(String.class), fullUrl);
        return parseResponse(response, WordPressContent.class, fullUrl);
    }

    /**
     * Fetches all posts from the WordPress site with pagination.
     */
    public List<WordPressContent> fetchAllPosts(String baseUrl, int page, int perPage) {
        String fullUrl = buildUrl(baseUrl, "/posts?page=" + page + "&per_page=" + perPage);
        log.info("Fetching WordPress posts: page={}, perPage={}", page, perPage);

        RestClient client = createClient(baseUrl);
        String response = executeWithRetry(() -> client.get()
                .uri(uriBuilder -> uriBuilder
                        .path(apiPath + "/posts")
                        .queryParam("page", page)
                        .queryParam("per_page", perPage)
                        .queryParam("_embed", true)
                        .build())
                .retrieve()
                .body(String.class), fullUrl);

        return parseResponseList(response, fullUrl);
    }

    /**
     * Fetches all pages from the WordPress site with pagination.
     */
    public List<WordPressContent> fetchAllPages(String baseUrl, int page, int perPage) {
        String fullUrl = buildUrl(baseUrl, "/pages?page=" + page + "&per_page=" + perPage);
        log.info("Fetching WordPress pages: page={}, perPage={}", page, perPage);

        RestClient client = createClient(baseUrl);
        String response = executeWithRetry(() -> client.get()
                .uri(uriBuilder -> uriBuilder
                        .path(apiPath + "/pages")
                        .queryParam("page", page)
                        .queryParam("per_page", perPage)
                        .queryParam("_embed", true)
                        .build())
                .retrieve()
                .body(String.class), fullUrl);

        return parseResponseList(response, fullUrl);
    }

    /**
     * Fetches content by slug (URL-friendly name).
     */
    public WordPressContent fetchBySlug(String baseUrl, String slug, String type) {
        log.info("Fetching WordPress content by slug: {}, type: {}", slug, type);

        String endpoint = "posts".equals(type) || "post".equals(type) ? "/posts" : "/pages";
        String fullUrl = buildUrl(baseUrl, endpoint + "?slug=" + slug);

        RestClient client = createClient(baseUrl);
        String response = executeWithRetry(() -> client.get()
                .uri(uriBuilder -> uriBuilder
                        .path(apiPath + endpoint)
                        .queryParam("slug", slug)
                        .build())
                .retrieve()
                .body(String.class), fullUrl);

        List<WordPressContent> results = parseResponseList(response, fullUrl);
        if (results.isEmpty()) {
            throw new WordPressApiException("No content found with slug: " + slug, fullUrl, 404);
        }
        return results.get(0);
    }

    private String executeWithRetry(String baseUrl, String uri, String fullUrl) {
        RestClient client = createClient(baseUrl);
        return executeWithRetry(() -> client.get()
                .uri(uri)
                .retrieve()
                .body(String.class), fullUrl);
    }

    private String executeWithRetry(ApiCall apiCall, String url) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < maxRetryAttempts) {
            attempts++;
            try {
                rateLimiter.acquireWp();
                return apiCall.execute();
            } catch (HttpClientErrorException.NotFound e) {
                log.warn("Content not found at URL: {}", url);
                throw new WordPressApiException("Content not found", url, 404, e);
            } catch (HttpClientErrorException e) {
                log.error("Client error fetching {}: {} - {}", url, e.getStatusCode(), e.getMessage());
                throw new WordPressApiException("Client error", url, e.getStatusCode().value(), e);
            } catch (HttpServerErrorException e) {
                log.warn("Server error (attempt {}/{}): {} - {}", attempts, maxRetryAttempts, url, e.getMessage());
                lastException = e;
                if (attempts < maxRetryAttempts) {
                    sleep(retryDelayMs * attempts);
                }
            } catch (ResourceAccessException e) {
                log.warn("Connection error (attempt {}/{}): {} - {}", attempts, maxRetryAttempts, url, e.getMessage());
                lastException = e;
                if (attempts < maxRetryAttempts) {
                    sleep(retryDelayMs * attempts);
                }
            }
        }

        log.error("Failed after {} attempts: {}", maxRetryAttempts, url);
        throw new WordPressApiException("Failed after " + maxRetryAttempts + " attempts", url, 0, lastException);
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private <T> T parseResponse(String response, Class<T> clazz, String url) {
        try {
            return objectMapper.readValue(response, clazz);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse response from {}: {}", url, e.getMessage());
            throw new ContentParsingException("Failed to parse WordPress API response", "JSON", e);
        }
    }

    private List<WordPressContent> parseResponseList(String response, String url) {
        try {
            return objectMapper.readValue(response, new TypeReference<List<WordPressContent>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse list response from {}: {}", url, e.getMessage());
            throw new ContentParsingException("Failed to parse WordPress API response list", "JSON", e);
        }
    }

    private String buildUrl(String baseUrl, String path) {
        return baseUrl.replaceAll("/$", "") + apiPath + path;
    }

    @FunctionalInterface
    private interface ApiCall {
        String execute();
    }
}
