package com.example.aemtransformer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bead for Zero-Downtime SEO Cutover (Bead 13).
 * Records WordPress Source URL -> AEM Target Path mappings.
 */
@Slf4j
@Service
public class SeoRedirectService {

    @Value("${aem.output-path:./output}")
    private String outputPath;

    @Value("${aem.site-path:/content/mysite}")
    private String sitePath;

    // Map of Original WP URL -> New AEM Path
    private final Map<String, String> redirectMap = new ConcurrentHashMap<>();

    /**
     * Records a redirect mapping.
     * @param sourceUrl The original WordPress URL (e.g., https://site.com/2023/slug)
     * @param aemPath The new AEM path (e.g., /content/mysite/en/slug)
     */
    public void recordRedirect(String sourceUrl, String aemPath) {
        if (sourceUrl == null || aemPath == null) return;
        
        // Normalize AEM Path to ensure it starts with /content
        String normalizedAemPath = aemPath;
        if (!normalizedAemPath.startsWith("/content")) {
            normalizedAemPath = (sitePath.endsWith("/") ? sitePath : sitePath + "/") + aemPath.replaceAll("^/", "");
        }
        
        // Ensure .html extension for AEM pages
        if (!normalizedAemPath.endsWith(".html") && !normalizedAemPath.contains("/dam/")) {
            normalizedAemPath += ".html";
        }

        redirectMap.put(sourceUrl, normalizedAemPath);
        log.debug("Recorded redirect: {} -> {}", sourceUrl, normalizedAemPath);
    }

    /**
     * Generates SEO redirect configuration files.
     */
    public void writeRedirectConfigs() {
        if (redirectMap.isEmpty()) {
            log.info("No redirects recorded; skipping config generation.");
            return;
        }

        try {
            Path outputDirPath = Path.of(outputPath, "seo-redirects");
            Files.createDirectories(outputDirPath);

            writeAemRedirectMap(outputDirPath.resolve("301-redirect-map.csv"));
            writeDispatcherConf(outputDirPath.resolve("dispatcher-redirects.conf"));
            
            log.info("SEO Redirect configs written to: {}", outputDirPath);
        } catch (IOException e) {
            log.error("Failed to write SEO redirect configs", e);
        }
    }

    private void writeAemRedirectMap(Path path) throws IOException {
        StringBuilder sb = new StringBuilder("Source,Target\n");
        redirectMap.forEach((src, tgt) -> {
            // Extract the path from the full WP URL for the mapping
            String srcPath = src.replaceFirst("https?://[^/]+", "");
            if (srcPath.isEmpty()) srcPath = "/";
            sb.append(srcPath).append(",").append(tgt).append("\n");
        });
        Files.writeString(path, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void writeDispatcherConf(Path path) throws IOException {
        StringBuilder sb = new StringBuilder("# Dispatcher Rewrite Rules for WP-to-AEM Migration\n");
        redirectMap.forEach((src, tgt) -> {
            String srcPath = src.replaceFirst("https?://[^/]+", "");
            if (srcPath.isEmpty()) srcPath = "/";
            // RewriteRule ^/old-path$ /new-path.html [R=301,L]
            sb.append("RewriteRule ^").append(srcPath).append("$ ").append(tgt).append(" [R=301,L]\n");
        });
        Files.writeString(path, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
