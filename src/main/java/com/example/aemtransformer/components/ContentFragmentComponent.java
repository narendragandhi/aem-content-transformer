package com.example.aemtransformer.components;

import com.example.aemtransformer.model.AemPage.ComponentNode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContentFragmentComponent implements AemComponent {

    private static final String RESOURCE_TYPE = "core/wcm/components/contentfragment/v1/contentfragment";

    private String fragmentPath;

    private String elementNames;

    private String id;

    @Builder.Default
    private String componentName = "contentfragment";

    @Override
    public String getResourceType() {
        return RESOURCE_TYPE;
    }

    @Override
    public ComponentNode toComponentNode() {
        ComponentNode node = ComponentNode.builder()
                .resourceType(RESOURCE_TYPE)
                .build();

        if (fragmentPath != null && !fragmentPath.isEmpty()) {
            node.addProperty("fragmentPath", fragmentPath);
        }
        if (elementNames != null && !elementNames.isEmpty()) {
            node.addProperty("elementNames", elementNames);
        }
        if (id != null && !id.isEmpty()) {
            node.addProperty("id", id);
        }
        return node;
    }
}
