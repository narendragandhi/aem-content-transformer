package com.example.aemtransformer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bead for Resilient Link Rewriting.
 * Maps WordPress asset URLs to AEM DAM paths.
 */
@Slf4j
@Service
public class AssetLinkRewriterService {

    @Value("${aem.site-path:/content/mysite}")
    private String sitePath;

    @Value("${aem.asset.dam-root:/content/dam/mysite}")
    private String damRoot;

    // Pattern for matching common asset URLs (images, pdfs, etc)
    private static final Pattern ASSET_URL_PATTERN = Pattern.compile(
            "https?://[^/\\s]+/(?:wp-content/uploads/|[^\\s]+?\\.(?:jpg|jpeg|png|gif|pdf|svg|webp))",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Translates a WordPress URL to an AEM DAM path.
     * Example: https://site.com/wp-content/uploads/2023/01/img.jpg -> /content/dam/mysite/2023/01/img.jpg
     */
    public String translateToDamPath(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return null;
        }

        // Only rewrite if it's an external URL
        if (!sourceUrl.startsWith("http")) {
            return sourceUrl;
        }

        String path = sourceUrl.replaceFirst("https?://[^/]+/", "");
        
        // Remove common WordPress prefixes to keep DAM clean
        path = path.replaceFirst("^wp-content/uploads/", "");
        
        // Clean the path (replace spaces with dashes, lowercase)
        String cleanPath = path.toLowerCase().replaceAll("[^a-z0-9/._-]", "-");
        
        String finalPath = (damRoot.endsWith("/") ? damRoot : damRoot + "/") + cleanPath;
        log.debug("Rewriting asset link: {} -> {}", sourceUrl, finalPath);
        return finalPath;
    }

    /**
     * Rewrites all asset URLs within a block of HTML text.
     */
    public String rewriteHtmlLinks(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }

        StringBuilder sb = new StringBuilder();
        Matcher matcher = ASSET_URL_PATTERN.matcher(html);
        int lastEnd = 0;

        while (matcher.find()) {
            sb.append(html, lastEnd, matcher.start());
            String sourceUrl = matcher.group();
            String damPath = translateToDamPath(sourceUrl);
            sb.append(damPath);
            lastEnd = matcher.end();
        }
        sb.append(html.substring(lastEnd));

        return sb.toString();
    }
}
