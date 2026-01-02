package com.example.aemtransformer.components;

import com.example.aemtransformer.model.AemPage.ComponentNode;
import lombok.Builder;
import lombok.Data;

/**
 * AEM Core Title Component.
 */
@Data
@Builder
public class TitleComponent implements AemComponent {

    private static final String RESOURCE_TYPE = "core/wcm/components/title/v3/title";

    private String text;

    @Builder.Default
    private String type = "h2";

    private String linkURL;

    private String id;

    @Builder.Default
    private String componentName = "title";

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public ComponentNode toComponentNode() {
        ComponentNode node = ComponentNode.builder()
                .resourceType(RESOURCE_TYPE)
                .build();

        node.addProperty("jcr:title", text);
        node.addProperty("type", type);

        if (linkURL != null && !linkURL.isEmpty()) {
            node.addProperty("linkURL", linkURL);
        }

        if (id != null && !id.isEmpty()) {
            node.addProperty("id", id);
        }

        return node;
    }
}
