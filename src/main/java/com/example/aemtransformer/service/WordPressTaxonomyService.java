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

    @Value("${wordpress.api-path:/wp-json/wp/v2}")
    private String apiPath;

    private final Map<Long, String> tagCache = new ConcurrentHashMap<>();

    public List<String> resolveTagNames(String baseUrl, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }

        List<String> names = new ArrayList<>();
        for (Long id : tagIds) {
            if (id == null) {
                continue;
            }
            String cached = tagCache.get(id);
            if (cached != null) {
                names.add(cached);
                continue;
            }
            String name = fetchTagName(baseUrl, id);
            if (name != null) {
                tagCache.put(id, name);
                names.add(name);
            }
        }
        return names;
    }

    private String fetchTagName(String baseUrl, Long tagId) {
        String endpoint = apiPath + "/tags/" + tagId;
        String fullUrl = baseUrl + endpoint;
        try {
            RestClient client = restClientBuilder.baseUrl(baseUrl).build();
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
}
