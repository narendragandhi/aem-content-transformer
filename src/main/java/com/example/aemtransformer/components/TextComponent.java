package com.example.aemtransformer.components;

import com.example.aemtransformer.model.AemPage.ComponentNode;
import lombok.Builder;
import lombok.Data;

/**
 * AEM Core Text Component.
 */
@Data
@Builder
public class TextComponent implements AemComponent {

    private static final String RESOURCE_TYPE = "core/wcm/components/text/v2/text";

    private String text;

    @Builder.Default
    private boolean textIsRich = true;

    private String id;

    @Builder.Default
    private String componentName = "text";

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public ComponentNode toComponentNode() {
        ComponentNode node = ComponentNode.builder()
                .resourceType(RESOURCE_TYPE)
                .build();

        node.addProperty("text", text);
        node.addProperty("textIsRich", textIsRich);

        if (id != null && !id.isEmpty()) {
            node.addProperty("id", id);
        }

        return node;
    }
}
