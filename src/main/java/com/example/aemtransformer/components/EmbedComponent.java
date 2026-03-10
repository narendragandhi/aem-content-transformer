package com.example.aemtransformer.components;

import com.example.aemtransformer.model.AemPage.ComponentNode;
import lombok.Builder;
import lombok.Data;

/**
 * AEM Core Embed Component.
 */
@Data
@Builder
public class EmbedComponent implements AemComponent {

    private static final String RESOURCE_TYPE = "core/wcm/components/embed/v2/embed";

    private String url;

    private String embedType;

    private String id;

    @Builder.Default
    private String componentName = "embed";

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public ComponentNode toComponentNode() {
        ComponentNode node = ComponentNode.builder()
                .resourceType(RESOURCE_TYPE)
                .build();

        if (url != null && !url.isEmpty()) {
            node.addProperty("url", url);
        }

        if (embedType != null && !embedType.isEmpty()) {
            node.addProperty("type", embedType);
        }

        if (id != null && !id.isEmpty()) {
            node.addProperty("id", id);
        }

        return node;
    }
}
