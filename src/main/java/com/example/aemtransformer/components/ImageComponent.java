package com.example.aemtransformer.components;

import com.example.aemtransformer.model.AemPage.ComponentNode;
import lombok.Builder;
import lombok.Data;

/**
 * AEM Core Image Component.
 */
@Data
@Builder
public class ImageComponent implements AemComponent {

    private static final String RESOURCE_TYPE = "core/wcm/components/image/v3/image";

    private String fileReference;

    private String alt;

    private String title;

    private String caption;

    private String linkURL;

    private String sourceUrl;

    @Builder.Default
    private boolean decorative = false;

    private String id;

    @Builder.Default
    private String componentName = "image";

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public ComponentNode toComponentNode() {
        ComponentNode node = ComponentNode.builder()
                .resourceType(RESOURCE_TYPE)
                .build();

        if (fileReference != null && !fileReference.isEmpty()) {
            node.addProperty("fileReference", fileReference);
        }

        if (alt != null && !alt.isEmpty()) {
            node.addProperty("alt", alt);
        }

        if (title != null && !title.isEmpty()) {
            node.addProperty("jcr:title", title);
        }

        if (caption != null && !caption.isEmpty()) {
            node.addProperty("caption", caption);
        }

        if (linkURL != null && !linkURL.isEmpty()) {
            node.addProperty("linkURL", linkURL);
        }

        if (sourceUrl != null && !sourceUrl.isEmpty()) {
            node.addProperty("sourceUrl", sourceUrl);
        }

        node.addProperty("isDecorative", decorative);

        if (id != null && !id.isEmpty()) {
            node.addProperty("id", id);
        }

        return node;
    }
}
