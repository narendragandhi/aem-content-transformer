package com.example.aemtransformer.components;

import com.example.aemtransformer.model.AemPage.ComponentNode;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AEM Core Container Component.
 */
@Data
@Builder
public class ContainerComponent implements AemComponent {

    private static final String RESOURCE_TYPE = "core/wcm/components/container/v1/container";

    @Builder.Default
    private String layout = "responsiveGrid";

    private String backgroundStyle;

    private String id;

    @Builder.Default
    private List<AemComponent> children = new ArrayList<>();

    @Builder.Default
    private String componentName = "container";

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    public void addChild(AemComponent component) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(component);
    }

    @Override
    public ComponentNode toComponentNode() {
        ComponentNode node = ComponentNode.builder()
                .resourceType(RESOURCE_TYPE)
                .build();

        node.addProperty("layout", layout);

        if (backgroundStyle != null && !backgroundStyle.isEmpty()) {
            node.addProperty("backgroundStyle", backgroundStyle);
        }

        if (id != null && !id.isEmpty()) {
            node.addProperty("id", id);
        }

        if (children != null) {
            for (AemComponent child : children) {
                node.addChild(child.getComponentName(), child.toComponentNode());
            }
        }

        return node;
    }
}
