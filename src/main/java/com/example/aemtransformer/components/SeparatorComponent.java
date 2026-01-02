package com.example.aemtransformer.components;

import com.example.aemtransformer.model.AemPage.ComponentNode;
import lombok.Builder;
import lombok.Data;

/**
 * AEM Core Separator Component.
 */
@Data
@Builder
public class SeparatorComponent implements AemComponent {

    private static final String RESOURCE_TYPE = "core/wcm/components/separator/v1/separator";

    private String id;

    @Builder.Default
    private String componentName = "separator";

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public ComponentNode toComponentNode() {
        ComponentNode node = ComponentNode.builder()
                .resourceType(RESOURCE_TYPE)
                .build();

        if (id != null && !id.isEmpty()) {
            node.addProperty("id", id);
        }

        return node;
    }
}
