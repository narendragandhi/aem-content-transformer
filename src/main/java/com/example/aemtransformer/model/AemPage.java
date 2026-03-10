package com.example.aemtransformer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an AEM page structure ready for JSON export.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AemPage implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("jcr:primaryType")
    @Builder.Default
    private String primaryType = "cq:Page";

    @JsonProperty("jcr:content")
    private PageContent content;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageContent implements Serializable {

        private static final long serialVersionUID = 1L;

        @JsonProperty("jcr:primaryType")
        @Builder.Default
        private String primaryType = "cq:PageContent";

        @JsonProperty("sling:resourceType")
        @Builder.Default
        private String resourceType = "core/wcm/components/page/v3/page";

        @JsonProperty("jcr:title")
        private String title;

        @JsonProperty("jcr:description")
        private String description;

        @JsonProperty("cq:tags")
        private String[] tags;

        private ComponentNode root;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentNode implements Serializable {

        private static final long serialVersionUID = 1L;

        @JsonProperty("jcr:primaryType")
        @Builder.Default
        private String primaryType = "nt:unstructured";

        @JsonProperty("sling:resourceType")
        private String resourceType;

        @Builder.Default
        private Map<String, Object> properties = new LinkedHashMap<>();

        @Builder.Default
        private Map<String, ComponentNode> children = new LinkedHashMap<>();

        public void addProperty(String key, Object value) {
            if (properties == null) {
                properties = new LinkedHashMap<>();
            }
            properties.put(key, value);
        }

        public void addChild(String name, ComponentNode child) {
            if (children == null) {
                children = new LinkedHashMap<>();
            }
            children.put(name, child);
        }
    }

    public static AemPage create(String title, String description) {
        return AemPage.builder()
                .content(PageContent.builder()
                        .title(title)
                        .description(description)
                        .root(ComponentNode.builder()
                                .resourceType("core/wcm/components/container/v1/container")
                                .build())
                        .build())
                .build();
    }
}
