package com.example.aemtransformer.util;

import com.example.aemtransformer.model.AemPage;
import com.example.aemtransformer.model.ComponentMapping;
import com.example.aemtransformer.model.ComponentMapping.AemComponentType;
import com.example.aemtransformer.model.ContentAnalysis;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MappingToPageConverterTest {

    @Test
    void toAemPage_withValidMappings_buildsPageStructure() {
        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle("Test Page")
                .pageDescription("Test description")
                .blocks(new ArrayList<>())
                .build();

        Map<String, Object> titleProps = new HashMap<>();
        titleProps.put("jcr:title", "Hello World");

        Map<String, Object> textProps = new HashMap<>();
        textProps.put("text", "<p>Some content</p>");

        List<ComponentMapping> mappings = List.of(
                ComponentMapping.builder()
                        .targetComponent(AemComponentType.TITLE)
                        .componentName("title_0")
                        .properties(titleProps)
                        .build(),
                ComponentMapping.builder()
                        .targetComponent(AemComponentType.TEXT)
                        .componentName("text_1")
                        .properties(textProps)
                        .build()
        );

        AemPage page = MappingToPageConverter.toAemPage(analysis, mappings);

        assertNotNull(page);
        assertNotNull(page.getContent());
        assertEquals("Test Page", page.getContent().getTitle());
        assertEquals("Test description", page.getContent().getDescription());

        assertNotNull(page.getContent().getRoot());
        assertEquals("core/wcm/components/container/v1/container",
                page.getContent().getRoot().getResourceType());

        // Check children were added
        assertEquals(2, page.getContent().getRoot().getChildren().size());
        assertTrue(page.getContent().getRoot().getChildren().containsKey("title_0"));
        assertTrue(page.getContent().getRoot().getChildren().containsKey("text_1"));
    }

    @Test
    void toAemPage_withEmptyMappings_createsEmptyContainer() {
        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle("Empty Page")
                .pageDescription("")
                .blocks(new ArrayList<>())
                .build();

        List<ComponentMapping> mappings = new ArrayList<>();

        AemPage page = MappingToPageConverter.toAemPage(analysis, mappings);

        assertNotNull(page);
        assertNotNull(page.getContent().getRoot());
        assertTrue(page.getContent().getRoot().getChildren().isEmpty());
    }

    @Test
    void toAemPage_withNullMappings_createsEmptyContainer() {
        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle("Null Mappings Page")
                .pageDescription("")
                .blocks(new ArrayList<>())
                .build();

        AemPage page = MappingToPageConverter.toAemPage(analysis, null);

        assertNotNull(page);
        assertNotNull(page.getContent().getRoot());
        assertTrue(page.getContent().getRoot().getChildren().isEmpty());
    }

    @Test
    void toAemPage_withNullAnalysis_createsPageWithEmptyTitleAndDescription() {
        List<ComponentMapping> mappings = new ArrayList<>();

        AemPage page = MappingToPageConverter.toAemPage(null, mappings);

        assertNotNull(page);
        assertEquals("", page.getContent().getTitle());
        assertEquals("", page.getContent().getDescription());
    }

    @Test
    void toAemPage_withNullComponentInMapping_skipsNullComponent() {
        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle("Test")
                .pageDescription("")
                .blocks(new ArrayList<>())
                .build();

        List<ComponentMapping> mappings = List.of(
                ComponentMapping.builder()
                        .targetComponent(AemComponentType.TITLE)
                        .componentName("title_0")
                        .properties(new HashMap<>())
                        .build(),
                ComponentMapping.builder()
                        .targetComponent(null) // Null target
                        .componentName("null_component")
                        .build()
        );

        AemPage page = MappingToPageConverter.toAemPage(analysis, mappings);

        // Should only have one child (the valid one)
        assertEquals(1, page.getContent().getRoot().getChildren().size());
    }

    @Test
    void toAemPage_componentWithoutName_generatesName() {
        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle("Test")
                .pageDescription("")
                .blocks(new ArrayList<>())
                .build();

        List<ComponentMapping> mappings = List.of(
                ComponentMapping.builder()
                        .targetComponent(AemComponentType.IMAGE)
                        .componentName(null) // No name provided
                        .properties(new HashMap<>())
                        .build()
        );

        AemPage page = MappingToPageConverter.toAemPage(analysis, mappings);

        // Should generate a name like "image_0"
        assertTrue(page.getContent().getRoot().getChildren().containsKey("image_0"));
    }

    @Test
    void toAemPage_rootContainerHasLayoutProperty() {
        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle("Test")
                .pageDescription("")
                .blocks(new ArrayList<>())
                .build();

        AemPage page = MappingToPageConverter.toAemPage(analysis, new ArrayList<>());

        assertEquals("responsiveGrid", page.getContent().getRoot().getProperties().get("layout"));
    }
}
