package com.example.aemtransformer.components;

import com.example.aemtransformer.model.AemPage.ComponentNode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExperienceFragmentComponent implements AemComponent {

    private static final String RESOURCE_TYPE = "core/wcm/components/experiencefragment/v1/experiencefragment";

    private String fragmentPath;

    private String id;

    @Builder.Default
    private String componentName = "experiencefragment";

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
        if (id != null && !id.isEmpty()) {
            node.addProperty("id", id);
        }
        return node;
    }
}
