package com.example.aemtransformer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class AemValidationService {

    private final RestClient.Builder restClientBuilder;
    private final RateLimiterService rateLimiter;

    @Value("${aem.validation.enabled:false}")
    private boolean validationEnabled;

    @Value("${aem.validation.base-url:}")
    private String baseUrl;

    @Value("${aem.validation.auth.type:}")
    private String authType;

    @Value("${aem.validation.auth.user:}")
    private String authUser;

    @Value("${aem.validation.auth.password:}")
    private String authPassword;

    @Value("${aem.validation.auth.token:}")
    private String authToken;

    @Value("${aem.validation.health-path:/system/health.json}")
    private String healthPath;

    public void checkHealth() {
        if (!validationEnabled) {
            return;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("AEM health check skipped (missing baseUrl)");
            return;
        }
        String url = baseUrl + (healthPath.startsWith("/") ? healthPath : "/" + healthPath);
        try {
            RestClient.RequestHeadersSpec<?> request = restClientBuilder.baseUrl(baseUrl).build()
                    .get()
                    .uri(url);
            applyAuth(request);
            rateLimiter.acquireAem();
            request.retrieve().toBodilessEntity();
            log.info("AEM health check OK");
        } catch (Exception e) {
            log.warn("AEM health check failed: {}", e.getMessage());
        }
    }

    public void validatePath(String label, String path) {
        if (!validationEnabled) {
            return;
        }
        if (baseUrl == null || baseUrl.isBlank() || path == null || path.isBlank()) {
            log.warn("AEM validation skipped for {} (missing baseUrl or path)", label);
            return;
        }

        String url = baseUrl + (path.startsWith("/") ? path : "/" + path) + ".json";
        try {
            RestClient.RequestHeadersSpec<?> request = restClientBuilder.baseUrl(baseUrl).build()
                    .get()
                    .uri(url);
            applyAuth(request);
            rateLimiter.acquireAem();
            request.retrieve().toBodilessEntity();
            log.info("AEM validation OK for {}: {}", label, path);
        } catch (Exception e) {
            log.warn("AEM validation failed for {}: {}", label, path);
        }
    }

    private void applyAuth(RestClient.RequestHeadersSpec<?> request) {
        String type = authType != null ? authType.trim().toLowerCase() : "";
        if ("basic".equals(type)) {
            if (authUser != null && authPassword != null) {
                String basic = java.util.Base64.getEncoder()
                        .encodeToString((authUser + ":" + authPassword).getBytes());
                request.header(HttpHeaders.AUTHORIZATION, "Basic " + basic);
            }
            return;
        }
        if ("bearer".equals(type)) {
            if (authToken != null && !authToken.isBlank()) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken.trim());
            }
        }
    }
}
