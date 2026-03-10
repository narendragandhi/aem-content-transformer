package com.example.aemtransformer.controller;

import com.example.aemtransformer.service.TransformationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST controller for transforming WordPress content into AEM JSON output.
 */
@RestController
@RequestMapping("/api/transform")
@RequiredArgsConstructor
public class AemTransformController {

    private final TransformationService transformationService;

    @Value("${transform.allowed-hosts:}")
    private String allowedHosts;

    /**
     * Transforms a WordPress post or page by URL and writes a JSON output file.
     *
     * @param url WordPress post or page URL
     * @return response including output path
     */
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public TransformResponse transform(@RequestParam String url) throws Exception {
        validateHostAllowlist(url);
        Path out = transformationService.transformByUrl(url);
        return new TransformResponse(out.toString());
    }

    private void validateHostAllowlist(String url) {
        if (allowedHosts == null || allowedHosts.isBlank()) {
            return;
        }

        Set<String> allowlist = Arrays.stream(allowedHosts.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        if (allowlist.isEmpty() || allowlist.contains("*")) {
            return;
        }

        String host;
        try {
            host = URI.create(url).getHost();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL");
        }

        if (host == null || host.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL host");
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        boolean allowed = allowlist.contains(normalizedHost)
                || allowlist.stream().anyMatch(entry -> normalizedHost.endsWith("." + entry));

        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL host not allowed");
        }
    }

    /**
     * Response payload for transform requests.
     *
     * @param outputPath output file path
     */
    public record TransformResponse(String outputPath) {}
}
