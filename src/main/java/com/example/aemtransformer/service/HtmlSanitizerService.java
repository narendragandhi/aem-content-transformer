package com.example.aemtransformer.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Sanitizes HTML fragments based on a configurable policy.
 */
@Slf4j
@Service
public class HtmlSanitizerService {

    private static final Document.OutputSettings OUTPUT_SETTINGS = new Document.OutputSettings()
            .prettyPrint(false);

    @Value("${aem.html.policy:relaxed}")
    private String policy;

    @Value("${aem.html.allowed-tags:}")
    private String allowedTags;

    @Value("${aem.html.allowed-attrs:}")
    private String allowedAttributes;

    @Value("${aem.html.allowed-protocols:}")
    private String allowedProtocols;

    private Safelist cachedSafelist;

    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.clean(html, "", getSafelist(), OUTPUT_SETTINGS);
    }

    private Safelist getSafelist() {
        if (cachedSafelist != null) {
            return cachedSafelist;
        }

        String normalized = policy == null ? "relaxed" : policy.trim().toLowerCase();
        Safelist safelist = switch (normalized) {
            case "none" -> Safelist.none();
            case "basic" -> Safelist.basic();
            case "simple" -> Safelist.simpleText();
            case "custom" -> buildCustomSafelist();
            default -> Safelist.relaxed();
        };

        if ("relaxed".equals(normalized)) {
            safelist.addAttributes("a", "target", "rel");
        }

        cachedSafelist = safelist;
        return cachedSafelist;
    }

    private Safelist buildCustomSafelist() {
        Safelist safelist = Safelist.none();
        addTags(safelist, allowedTags);
        addAttributes(safelist, allowedAttributes);
        addProtocols(safelist, allowedProtocols);
        return safelist;
    }

    private void addTags(Safelist safelist, String tags) {
        List<String> parsed = splitCsv(tags);
        if (!parsed.isEmpty()) {
            safelist.addTags(parsed.toArray(new String[0]));
        }
    }

    private void addAttributes(Safelist safelist, String attrs) {
        for (String entry : splitCsv(attrs)) {
            String[] parts = entry.split("\\.", 2);
            if (parts.length != 2) {
                continue;
            }
            String tag = "*".equals(parts[0]) ? ":all" : parts[0];
            String attr = parts[1];
            safelist.addAttributes(tag, attr);
        }
    }

    private void addProtocols(Safelist safelist, String protocols) {
        for (String entry : splitCsv(protocols)) {
            String[] parts = entry.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            String tagAttr = parts[0];
            String[] tagAttrParts = tagAttr.split("\\.", 2);
            if (tagAttrParts.length != 2) {
                continue;
            }
            String tag = "*".equals(tagAttrParts[0]) ? ":all" : tagAttrParts[0];
            String attr = tagAttrParts[1];
            String[] protoParts = parts[1].split("\\|");
            safelist.addProtocols(tag, attr, protoParts);
        }
    }

    private List<String> splitCsv(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return result;
        }
        for (String part : trimmed.split(",")) {
            String item = part.trim();
            if (!item.isEmpty()) {
                result.add(item);
            }
        }
        return result;
    }
}
