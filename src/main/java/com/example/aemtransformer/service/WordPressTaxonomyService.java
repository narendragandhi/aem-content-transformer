package com.example.aemtransformer.service;

import com.example.aemtransformer.exception.WordPressApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WordPressTaxonomyService {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final RateLimiterService rateLimiter;

    @Value("${wordpress.api-path:/wp-json/wp/v2}")
    private String apiPath;

    private final Map<Long, String> tagCache = new ConcurrentHashMap<>();

    public List<String> resolveTagNames(String baseUrl, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }

        List<Long> missing = new ArrayList<>();
        List<String> names = new ArrayList<>();

        for (Long id : tagIds) {
            if (id == null) {
                continue;
            }
            String cached = tagCache.get(id);
            if (cached != null) {
                names.add(cached);
            } else {
                missing.add(id);
            }
        }

        if (!missing.isEmpty()) {
            Map<Long, String> resolved = fetchTagNamesBulk(baseUrl, missing);
            for (Long id : missing) {
                String name = resolved.get(id);
                if (name != null) {
                    tagCache.put(id, name);
                    names.add(name);
                }
            }
        }

        return names;
    }

    private String fetchTagName(String baseUrl, Long tagId) {
        String endpoint = apiPath + "/tags/" + tagId;
        String fullUrl = baseUrl + endpoint;
        try {
            RestClient client = restClientBuilder.baseUrl(baseUrl).build();
            rateLimiter.acquireWp();
            String response = client.get()
                    .uri(endpoint)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(response);
            JsonNode nameNode = node.get("name");
            return nameNode != null ? nameNode.asText() : null;
        } catch (Exception e) {
            throw new WordPressApiException("Failed to resolve tag " + tagId, fullUrl, 0, e);
        }
    }

    private Map<Long, String> fetchTagNamesBulk(String baseUrl, List<Long> tagIds) {
        Map<Long, String> resolved = new ConcurrentHashMap<>();
        if (tagIds == null || tagIds.isEmpty()) {
            return resolved;
        }

        String ids = tagIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        String endpoint = apiPath + "/tags?include=" + ids + "&per_page=" + Math.max(tagIds.size(), 1);
        String fullUrl = baseUrl + endpoint;

        try {
            RestClient client = restClientBuilder.baseUrl(baseUrl).build();
            rateLimiter.acquireWp();
            String response = client.get()
                    .uri(endpoint)
                    .retrieve()
                    .body(String.class);
            JsonNode array = objectMapper.readTree(response);
            if (array != null && array.isArray()) {
                for (JsonNode node : array) {
                    JsonNode idNode = node.get("id");
                    JsonNode nameNode = node.get("name");
                    if (idNode != null && nameNode != null) {
                        resolved.put(idNode.asLong(), nameNode.asText());
                    }
                }
            }
            return resolved;
        } catch (Exception e) {
            log.warn("Bulk tag resolution failed; falling back to per-tag fetch. {}", e.getMessage());
            for (Long id : tagIds) {
                try {
                    String name = fetchTagName(baseUrl, id);
                    if (name != null) {
                        resolved.put(id, name);
                    }
                } catch (Exception ex) {
                    log.warn("Failed to resolve tag {} in fallback", id);
                }
            }
            return resolved;
        }
    }
}
