package com.example.aemtransformer.agent;

import com.example.aemtransformer.model.ComponentMapping;
import com.example.aemtransformer.model.ComponentMapping.AemComponentType;
import com.example.aemtransformer.model.ContentAnalysis;
import com.example.aemtransformer.model.ContentBlock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent responsible for mapping content blocks to AEM Core Components.
 * Uses rule-based mapping for consistent conversions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComponentMapperAgent {

    private static final String DAM_MIGRATION_ROOT = "/content/dam/migration";
    private static final Safelist AEM_RTE_SAFELIST = Safelist.relaxed()
            .addAttributes("a", "target", "rel");
    private static final Document.OutputSettings AEM_OUTPUT_SETTINGS = new Document.OutputSettings()
            .prettyPrint(false);

    /**
     * Maps analyzed content blocks to AEM Core Components.
     *
     * @param analysis content analysis results
     * @return list of component mappings
     */
    public List<ComponentMapping> mapComponents(ContentAnalysis analysis) {
        log.info("Mapping {} content blocks to AEM components", analysis.getBlocks().size());

        List<ComponentMapping> mappings = new ArrayList<>();

        for (int i = 0; i < analysis.getBlocks().size(); i++) {
            ContentBlock block = analysis.getBlocks().get(i);
            ComponentMapping mapping = mapBlock(block, i);
            if (mapping != null) {
                mappings.add(mapping);
            }
        }

        log.info("Created {} component mappings", mappings.size());
        return mappings;
    }

    private ComponentMapping mapBlock(ContentBlock block, int index) {
        AemComponentType componentType = determineComponentType(block);

        if (componentType == null) {
            log.debug("Skipping unmappable block at index {}: {}", index, block.getType());
            return null;
        }

        Map<String, Object> properties = extractProperties(block, componentType);
        String componentName = generateComponentName(componentType, index);

        return ComponentMapping.builder()
                .sourceBlockIndex(index)
                .sourceBlock(block)
                .targetComponent(componentType)
                .componentName(componentName)
                .properties(properties)
                .mappingRationale(generateRationale(block, componentType))
                .confidence(calculateConfidence(block, componentType))
                .build();
    }

    private AemComponentType determineComponentType(ContentBlock block) {
        return switch (block.getType()) {
            case HEADING -> AemComponentType.TITLE;
            case PARAGRAPH, QUOTE, CODE, TABLE -> AemComponentType.TEXT;
            case IMAGE -> AemComponentType.IMAGE;
            case LIST -> AemComponentType.TEXT;
            case GALLERY -> AemComponentType.CAROUSEL;
            case SEPARATOR -> AemComponentType.SEPARATOR;
            case EMBED -> AemComponentType.EMBED;
            case UNKNOWN -> determineUnknownBlockType(block);
        };
    }

    private AemComponentType determineUnknownBlockType(ContentBlock block) {
        if (block.getChildren() != null && !block.getChildren().isEmpty()) {
            return AemComponentType.CONTAINER;
        }

        if (block.getContent() != null && !block.getContent().trim().isEmpty()) {
            return AemComponentType.TEXT;
        }

        return null;
    }

    private Map<String, Object> extractProperties(ContentBlock block, AemComponentType componentType) {
        Map<String, Object> props = new HashMap<>();

        switch (componentType) {
            case TITLE -> {
                props.put("jcr:title", block.getContent());
                props.put("type", "h" + Math.min(block.getHeadingLevel(), 6));
            }
            case TEXT -> {
                props.put("text", formatTextContent(block));
                props.put("textIsRich", true);
            }
            case LIST -> {
                props.put("items", block.getListItems());
                props.put("ordered", block.isOrdered());
            }
            case IMAGE -> {
                props.put("fileReference", mapImageReference(block.getImageUrl()));
                props.put("alt", block.getImageAlt());
                if (block.getImageCaption() != null) {
                    props.put("caption", block.getImageCaption());
                }
            }
            case SEPARATOR -> {
                // No additional properties needed
            }
            case EMBED -> {
                props.put("url", block.getContent());
                if (block.getAttributes() != null) {
                    props.put("embedType", block.getAttributes().get("embedType"));
                }
            }
            case CAROUSEL -> {
                if (block.getChildren() != null) {
                    List<Map<String, String>> items = new ArrayList<>();
                    for (ContentBlock child : block.getChildren()) {
                        Map<String, String> item = new HashMap<>();
                        item.put("fileReference", mapImageReference(child.getImageUrl()));
                        item.put("alt", child.getImageAlt());
                        items.add(item);
                    }
                    props.put("items", items);
                }
            }
            case CONTAINER -> {
                props.put("layout", "responsiveGrid");
            }
            default -> {
                // Default handling
            }
        }

        return props;
    }

    private String formatTextContent(ContentBlock block) {
        return switch (block.getType()) {
            case PARAGRAPH -> "<p>" + sanitizeHtmlFragment(block.getContent()) + "</p>";
            case QUOTE -> "<blockquote>" + sanitizeHtmlFragment(block.getContent()) + "</blockquote>";
            case CODE -> "<pre><code>" + escapeHtml(block.getContent()) + "</code></pre>";
            case TABLE -> sanitizeHtmlFragment(block.getRawHtml());
            case LIST -> formatListContent(block);
            default -> sanitizeHtmlFragment(block.getContent() != null ? block.getContent() : block.getRawHtml());
        };
    }

    private String formatListContent(ContentBlock block) {
        if (block.getListItems() == null || block.getListItems().isEmpty()) {
            return block.getRawHtml();
        }

        StringBuilder sb = new StringBuilder();
        String tag = block.isOrdered() ? "ol" : "ul";
        sb.append("<").append(tag).append(">");
        for (String item : block.getListItems()) {
            sb.append("<li>").append(sanitizeHtmlFragment(item)).append("</li>");
        }
        sb.append("</").append(tag).append(">");
        return sb.toString();
    }

    private String sanitizeHtmlFragment(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.clean(html, "", AEM_RTE_SAFELIST, AEM_OUTPUT_SETTINGS);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String mapImageReference(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        String trimmed = imageUrl.trim();
        if (trimmed.startsWith("/content/dam/")) {
            return trimmed;
        }

        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return trimmed;
        }

        try {
            URI uri = new URI(trimmed);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return trimmed;
            }

            String path = uri.getPath();
            List<String> segments = new ArrayList<>();
            segments.add("content");
            segments.add("dam");
            segments.add("migration");
            segments.add(sanitizeSegment(host));

            if (path != null && !path.isBlank()) {
                for (String segment : path.split("/")) {
                    if (!segment.isBlank()) {
                        segments.add(sanitizeSegment(segment));
                    }
                }
            } else {
                segments.add("image");
            }

            return "/" + String.join("/", segments);
        } catch (URISyntaxException e) {
            return trimmed;
        }
    }

    private String sanitizeSegment(String segment) {
        String cleaned = segment.trim().replaceAll("[^a-zA-Z0-9._-]", "-");
        cleaned = cleaned.replaceAll("-{2,}", "-");
        if (cleaned.isBlank()) {
            return "asset";
        }
        return cleaned;
    }

    private String generateComponentName(AemComponentType type, int index) {
        String baseName = switch (type) {
            case TITLE -> "title";
            case TEXT -> "text";
            case IMAGE -> "image";
            case LIST -> "list";
            case CAROUSEL -> "carousel";
            case SEPARATOR -> "separator";
            case EMBED -> "embed";
            case CONTAINER -> "container";
            default -> "component";
        };
        return baseName + "_" + index;
    }

    private String generateRationale(ContentBlock block, AemComponentType componentType) {
        return "Mapped " + block.getType() + " block to " + componentType.name() +
                " based on content structure analysis";
    }

    private double calculateConfidence(ContentBlock block, AemComponentType componentType) {
        return switch (block.getType()) {
            case HEADING, PARAGRAPH, IMAGE, LIST, SEPARATOR -> 0.95;
            case QUOTE, CODE -> 0.90;
            case TABLE, EMBED -> 0.85;
            case GALLERY -> 0.80;
            case UNKNOWN -> 0.60;
        };
    }
}
