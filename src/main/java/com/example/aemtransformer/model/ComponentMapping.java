package com.example.aemtransformer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Mapping from a content block to an AEM Core Component.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentMapping {

    public enum AemComponentType {
        TITLE("core/wcm/components/title/v3/title"),
        TEXT("core/wcm/components/text/v2/text"),
        IMAGE("core/wcm/components/image/v3/image"),
        LIST("core/wcm/components/list/v4/list"),
        TEASER("core/wcm/components/teaser/v2/teaser"),
        CAROUSEL("core/wcm/components/carousel/v1/carousel"),
        SEPARATOR("core/wcm/components/separator/v1/separator"),
        EMBED("core/wcm/components/embed/v2/embed"),
        CONTAINER("core/wcm/components/container/v1/container");

        private final String resourceType;

        AemComponentType(String resourceType) {
            this.resourceType = resourceType;
        }

        public String getResourceType() {
            return resourceType;
        }
    }

    private int sourceBlockIndex;

    private ContentBlock sourceBlock;

    private AemComponentType targetComponent;

    private String componentName;

    private Map<String, Object> properties;

    private String mappingRationale;

    private double confidence;
}
