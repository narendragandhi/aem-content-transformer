package com.example.aemtransformer.components;

import com.example.aemtransformer.model.AemPage.ComponentNode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * AEM Core List Component.
 */
@Data
@Builder
public class ListComponent implements AemComponent {

    private static final String RESOURCE_TYPE = "core/wcm/components/list/v4/list";

    private List<String> items;

    @Builder.Default
    private boolean ordered = false;

    private String id;

    @Builder.Default
    private String componentName = "list";

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public ComponentNode toComponentNode() {
        ComponentNode node = ComponentNode.builder()
                .resourceType(RESOURCE_TYPE)
                .build();

        if (items != null && !items.isEmpty()) {
            StringBuilder html = new StringBuilder();
            String tag = ordered ? "ol" : "ul";
            html.append("<").append(tag).append(">");
            for (String item : items) {
                html.append("<li>").append(item).append("</li>");
            }
            html.append("</").append(tag).append(">");
            node.addProperty("text", html.toString());
            node.addProperty("textIsRich", true);
        }

        if (id != null && !id.isEmpty()) {
            node.addProperty("id", id);
        }

        return node;
    }
}
