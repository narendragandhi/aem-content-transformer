package com.example.aemtransformer.components;

import com.example.aemtransformer.model.AemPage.ComponentNode;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AEM Core Carousel Component.
 */
@Data
@Builder
public class CarouselComponent implements AemComponent {

    private static final String RESOURCE_TYPE = "core/wcm/components/carousel/v1/carousel";

    @Builder.Default
    private List<AemComponent> items = new ArrayList<>();

    private String id;

    @Builder.Default
    private String componentName = "carousel";

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    public void addItem(AemComponent component) {
        if (items == null) {
            items = new ArrayList<>();
        }
        items.add(component);
    }

    @Override
    public ComponentNode toComponentNode() {
        ComponentNode node = ComponentNode.builder()
                .resourceType(RESOURCE_TYPE)
                .build();

        if (id != null && !id.isEmpty()) {
            node.addProperty("id", id);
        }

        if (items != null) {
            for (AemComponent item : items) {
                node.addChild(item.getComponentName(), item.toComponentNode());
            }
        }

        return node;
    }
}
