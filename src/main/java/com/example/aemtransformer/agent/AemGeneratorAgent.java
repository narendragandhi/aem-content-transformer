package com.example.aemtransformer.agent;

import com.example.aemtransformer.components.*;
import com.example.aemtransformer.model.AemPage;
import com.example.aemtransformer.model.AemPage.ComponentNode;
import com.example.aemtransformer.model.AemPage.PageContent;
import com.example.aemtransformer.model.ComponentMapping;
import com.example.aemtransformer.model.ComponentMapping.AemComponentType;
import com.example.aemtransformer.model.ContentAnalysis;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Agent responsible for generating AEM page structures from component mappings.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AemGeneratorAgent {

    @Value("${aem.template-path:/conf/mysite/settings/wcm/templates/content-page}")
    private String templatePath = "/conf/mysite/settings/wcm/templates/content-page";

    @Value("${aem.design-path:}")
    private String designPath = "";

    @Value("${aem.allowed-templates:}")
    private String allowedTemplates = "";

    @Value("${aem.cloudservice-configs:}")
    private String cloudServiceConfigs = "";

    /**
     * Generates an AEM page from content analysis and component mappings.
     *
     * @param analysis content analysis results
     * @param mappings component mappings
     * @return generated AEM page
     */
    public AemPage generate(ContentAnalysis analysis, List<ComponentMapping> mappings) {
        log.info("Generating AEM page: {} with {} components",
                analysis.getPageTitle(), mappings.size());

        ContainerComponent rootContainer = ContainerComponent.builder()
                .componentName("root")
                .layout("responsiveGrid")
                .build();

        for (ComponentMapping mapping : mappings) {
            AemComponent component = createComponent(mapping);
            if (component != null) {
                rootContainer.addChild(component);
            }
        }

        ComponentNode rootNode = rootContainer.toComponentNode();

        PageContent pageContent = PageContent.builder()
                .title(analysis.getPageTitle())
                .description(analysis.getPageDescription())
                .template(resolveOptional(templatePath))
                .designPath(resolveOptional(designPath))
                .allowedTemplates(splitCsv(allowedTemplates))
                .cloudServiceConfigs(splitCsv(cloudServiceConfigs))
                .root(rootNode)
                .build();

        AemPage page = AemPage.builder()
                .content(pageContent)
                .build();

        log.info("AEM page generated successfully");
        return page;
    }

    private String resolveOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String[] splitCsv(String value) {
        String trimmed = resolveOptional(value);
        if (trimmed == null) {
            return null;
        }
        String[] parts = trimmed.split(",");
        List<String> cleaned = new java.util.ArrayList<>();
        for (String part : parts) {
            String segment = part.trim();
            if (!segment.isEmpty()) {
                cleaned.add(segment);
            }
        }
        return cleaned.isEmpty() ? null : cleaned.toArray(new String[0]);
    }

    private AemComponent createComponent(ComponentMapping mapping) {
        AemComponentType type = mapping.getTargetComponent();

        return switch (type) {
            case TITLE -> createTitleComponent(mapping);
            case TEXT -> createTextComponent(mapping);
            case IMAGE -> createImageComponent(mapping);
            case LIST -> createListComponent(mapping);
            case SEPARATOR -> createSeparatorComponent(mapping);
            case CAROUSEL -> createCarouselComponent(mapping);
            case EMBED -> createEmbedComponent(mapping);
            case CONTAINER -> createContainerComponent(mapping);
            default -> {
                log.warn("Unsupported component type: {}", type);
                yield null;
            }
        };
    }

    private TitleComponent createTitleComponent(ComponentMapping mapping) {
        Map<String, Object> props = mapping.getProperties();
        return TitleComponent.builder()
                .componentName(mapping.getComponentName())
                .text(getString(props, "jcr:title"))
                .type(getString(props, "type", "h2"))
                .linkURL(getString(props, "linkURL"))
                .build();
    }

    private TextComponent createTextComponent(ComponentMapping mapping) {
        Map<String, Object> props = mapping.getProperties();
        return TextComponent.builder()
                .componentName(mapping.getComponentName())
                .text(getString(props, "text"))
                .textIsRich(getBoolean(props, "textIsRich", true))
                .build();
    }

    private ImageComponent createImageComponent(ComponentMapping mapping) {
        Map<String, Object> props = mapping.getProperties();
        return ImageComponent.builder()
                .componentName(mapping.getComponentName())
                .fileReference(getString(props, "fileReference"))
                .alt(getString(props, "alt"))
                .caption(getString(props, "caption"))
                .sourceUrl(getString(props, "sourceUrl"))
                .build();
    }

    private ListComponent createListComponent(ComponentMapping mapping) {
        Map<String, Object> props = mapping.getProperties() != null ? mapping.getProperties() : Map.of();

        @SuppressWarnings("unchecked")
        List<String> items = (List<String>) props.get("items");

        return ListComponent.builder()
                .componentName(mapping.getComponentName())
                .items(items)
                .ordered(getBoolean(props, "ordered", false))
                .build();
    }

    private SeparatorComponent createSeparatorComponent(ComponentMapping mapping) {
        return SeparatorComponent.builder()
                .componentName(mapping.getComponentName())
                .build();
    }

    private CarouselComponent createCarouselComponent(ComponentMapping mapping) {
        Map<String, Object> props = mapping.getProperties() != null ? mapping.getProperties() : Map.of();

        CarouselComponent carousel = CarouselComponent.builder()
                .componentName(mapping.getComponentName())
                .build();

        @SuppressWarnings("unchecked")
        List<Map<String, String>> items = (List<Map<String, String>>) props.get("items");
        if (items != null) {
            int index = 0;
            for (Map<String, String> item : items) {
                    ImageComponent image = ImageComponent.builder()
                        .componentName("carousel_image_" + index++)
                        .fileReference(item.get("fileReference"))
                        .alt(item.get("alt"))
                        .sourceUrl(item.get("sourceUrl"))
                        .build();
                    carousel.addItem(image);
                }
        }

        return carousel;
    }

    private AemComponent createEmbedComponent(ComponentMapping mapping) {
        Map<String, Object> props = mapping.getProperties() != null ? mapping.getProperties() : Map.of();
        String url = getString(props, "url");
        String sanitizedUrl = sanitizeUrl(url);

        if (sanitizedUrl == null) {
            log.warn("Invalid or unsafe embed URL rejected: {}", url);
            return TextComponent.builder()
                    .componentName(mapping.getComponentName())
                    .text("<div class=\"embed-container\"><p>Embed content unavailable</p></div>")
                    .textIsRich(true)
                    .build();
        }

        return EmbedComponent.builder()
                .componentName(mapping.getComponentName())
                .url(sanitizedUrl)
                .embedType(getString(props, "embedType"))
                .build();
    }

    /**
     * Sanitizes a URL to prevent XSS attacks.
     * Only allows http/https protocols and escapes special characters.
     */
    private String sanitizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        String trimmed = url.trim();

        // Only allow http and https protocols
        if (!trimmed.toLowerCase().startsWith("http://") && !trimmed.toLowerCase().startsWith("https://")) {
            return null;
        }

        // Block javascript: and data: URLs that might be disguised
        String lowerUrl = trimmed.toLowerCase();
        if (lowerUrl.contains("javascript:") || lowerUrl.contains("data:") || lowerUrl.contains("vbscript:")) {
            return null;
        }

        // Escape HTML special characters to prevent attribute breakout
        return escapeHtmlAttribute(trimmed);
    }

    private String escapeHtmlAttribute(String value) {
        if (value == null) return null;
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private ContainerComponent createContainerComponent(ComponentMapping mapping) {
        return ContainerComponent.builder()
                .componentName(mapping.getComponentName())
                .layout(getString(mapping.getProperties(), "layout", "responsiveGrid"))
                .build();
    }

    private String getString(Map<String, Object> props, String key) {
        return getString(props, key, null);
    }

    private String getString(Map<String, Object> props, String key, String defaultValue) {
        if (props == null) return defaultValue;
        Object value = props.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private boolean getBoolean(Map<String, Object> props, String key, boolean defaultValue) {
        if (props == null) return defaultValue;
        Object value = props.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
}
