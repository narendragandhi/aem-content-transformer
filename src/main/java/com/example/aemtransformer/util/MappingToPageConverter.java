package com.example.aemtransformer.util;

import com.example.aemtransformer.model.AemPage;
import com.example.aemtransformer.model.AemPage.ComponentNode;
import com.example.aemtransformer.model.ComponentMapping;
import com.example.aemtransformer.model.ContentAnalysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MappingToPageConverter {

    private static final String CONTAINER_RESOURCE_TYPE = "core/wcm/components/container/v1/container";

    public static AemPage toAemPage(ContentAnalysis analysis, List<ComponentMapping> mappings) {
        ComponentNode root = buildRootContainer(mappings);

        return AemPage.builder()
            .content(AemPage.PageContent.builder()
                .title(analysis != null ? analysis.getPageTitle() : "")
                .description(analysis != null ? analysis.getPageDescription() : "")
                .root(root)
                .build())
            .build();
    }

    private static ComponentNode buildRootContainer(List<ComponentMapping> mappings) {
        ComponentNode root = ComponentNode.builder()
            .resourceType(CONTAINER_RESOURCE_TYPE)
            .properties(new LinkedHashMap<>())
            .children(new LinkedHashMap<>())
            .build();

        root.addProperty("layout", "responsiveGrid");

        if (mappings == null || mappings.isEmpty()) {
            return root;
        }

        for (int i = 0; i < mappings.size(); i++) {
            ComponentMapping mapping = mappings.get(i);
            if (mapping == null || mapping.getTargetComponent() == null) {
                continue;
            }

            String componentName = generateComponentName(mapping, i);
            ComponentNode componentNode = buildComponentNode(mapping);

            root.addChild(componentName, componentNode);
        }

        return root;
    }

    private static ComponentNode buildComponentNode(ComponentMapping mapping) {
        Map<String, Object> nodeProperties = new LinkedHashMap<>();

        if (mapping.getProperties() != null) {
            nodeProperties.putAll(mapping.getProperties());
        }

        return ComponentNode.builder()
            .resourceType(mapping.getTargetComponent().getResourceType())
            .properties(nodeProperties)
            .children(new LinkedHashMap<>())
            .build();
    }

    private static String generateComponentName(ComponentMapping mapping, int index) {
        if (mapping.getComponentName() != null && !mapping.getComponentName().isBlank()) {
            return sanitizeNodeName(mapping.getComponentName());
        }

        String typeName = mapping.getTargetComponent().name().toLowerCase();
        return typeName + "_" + index;
    }

    private static String sanitizeNodeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
    }
}
