package com.example.aemtransformer.agent;

import com.example.aemtransformer.model.AemPage;
import com.example.aemtransformer.model.ComponentMapping;
import com.example.aemtransformer.model.ComponentMapping.AemComponentType;
import com.example.aemtransformer.model.ContentAnalysis;
import com.example.aemtransformer.model.ContentBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AemGeneratorAgentTest {

    private AemGeneratorAgent agent;

    @BeforeEach
    void setUp() {
        agent = new AemGeneratorAgent();
    }

    @Test
    void generate_withValidMappings_createsPage() {
        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle("Test Page")
                .pageDescription("Description")
                .blocks(new ArrayList<>())
                .build();

        Map<String, Object> props = new HashMap<>();
        props.put("jcr:title", "Title");

        List<ComponentMapping> mappings = List.of(
                ComponentMapping.builder()
                        .targetComponent(AemComponentType.TITLE)
                        .componentName("title_0")
                        .properties(props)
                        .build()
        );

        AemPage page = agent.generate(analysis, mappings);

        assertNotNull(page);
        assertEquals("Test Page", page.getContent().getTitle());
        assertNotNull(page.getContent().getRoot());
    }

    @Test
    void generate_embedComponent_sanitizesUrl() {
        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle("Test")
                .pageDescription("")
                .blocks(new ArrayList<>())
                .build();

        Map<String, Object> props = new HashMap<>();
        props.put("url", "https://youtube.com/embed/abc123");

        List<ComponentMapping> mappings = List.of(
                ComponentMapping.builder()
                        .targetComponent(AemComponentType.EMBED)
                        .componentName("embed_0")
                        .properties(props)
                        .build()
        );

        AemPage page = agent.generate(analysis, mappings);

        assertNotNull(page);
        var embedChild = page.getContent().getRoot().getChildren().get("embed_0");
        assertNotNull(embedChild);
        assertEquals("core/wcm/components/embed/v2/embed", embedChild.getResourceType());
        assertEquals("https://youtube.com/embed/abc123", embedChild.getProperties().get("url"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert('xss')",
            "data:text/html,<script>alert(1)</script>",
            "vbscript:msgbox('xss')"
    })
    void generate_embedWithMaliciousUrl_rejectsMaliciousProtocols(String maliciousUrl) {
        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle("Test")
                .pageDescription("")
                .blocks(new ArrayList<>())
                .build();

        Map<String, Object> props = new HashMap<>();
        props.put("url", maliciousUrl);

        List<ComponentMapping> mappings = List.of(
                ComponentMapping.builder()
                        .targetComponent(AemComponentType.EMBED)
                        .componentName("embed_0")
                        .properties(props)
                        .build()
        );

        AemPage page = agent.generate(analysis, mappings);

        // The component should be created with fallback content
        var embedChild = page.getContent().getRoot().getChildren().get("embed_0");
        assertNotNull(embedChild);

        // Verify the malicious URL is not in the output
        String text = (String) embedChild.getProperties().get("text");
        assertFalse(text.contains(maliciousUrl));
        assertTrue(text.contains("unavailable"));
    }

    @Test
    void generate_embedWithXssAttempt_escapesHtmlCharacters() {
        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle("Test")
                .pageDescription("")
                .blocks(new ArrayList<>())
                .build();

        Map<String, Object> props = new HashMap<>();
        props.put("url", "https://example.com/embed?x=\"><script>alert(1)</script>");

        List<ComponentMapping> mappings = List.of(
                ComponentMapping.builder()
                        .targetComponent(AemComponentType.EMBED)
                        .componentName("embed_0")
                        .properties(props)
                        .build()
        );

        AemPage page = agent.generate(analysis, mappings);

        var embedChild = page.getContent().getRoot().getChildren().get("embed_0");
        String url = (String) embedChild.getProperties().get("url");
        String text = (String) embedChild.getProperties().get("text");

        if (url != null) {
            assertFalse(url.contains("<script>"), "Embed URL should be sanitized");
            assertTrue(url.contains("&lt;script&gt;") || url.contains("&quot;"),
                    "Embed URL should escape dangerous characters");
        } else {
            assertNotNull(text, "Fallback text should be present if URL rejected");
            assertTrue(text.contains("unavailable"));
        }
    }

    @Test
    void generate_embedWithNullUrl_handlesgracefully() {
        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle("Test")
                .pageDescription("")
                .blocks(new ArrayList<>())
                .build();

        Map<String, Object> props = new HashMap<>();
        props.put("url", null);

        List<ComponentMapping> mappings = List.of(
                ComponentMapping.builder()
                        .targetComponent(AemComponentType.EMBED)
                        .componentName("embed_0")
                        .properties(props)
                        .build()
        );

        AemPage page = agent.generate(analysis, mappings);

        var embedChild = page.getContent().getRoot().getChildren().get("embed_0");
        String text = (String) embedChild.getProperties().get("text");
        assertTrue(text.contains("unavailable"));
    }

    @Test
    void generate_listComponent_setsItems() {
        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle("Test")
                .pageDescription("")
                .blocks(new ArrayList<>())
                .build();

        Map<String, Object> props = new HashMap<>();
        props.put("items", List.of("A", "B"));
        props.put("ordered", true);

        List<ComponentMapping> mappings = List.of(
                ComponentMapping.builder()
                        .targetComponent(AemComponentType.LIST)
                        .componentName("list_0")
                        .properties(props)
                        .build()
        );

        AemPage page = agent.generate(analysis, mappings);

        var listChild = page.getContent().getRoot().getChildren().get("list_0");
        assertNotNull(listChild);
        assertEquals("core/wcm/components/list/v4/list", listChild.getResourceType());
        assertTrue(((String) listChild.getProperties().get("text")).contains("<ol>"));
    }

    @Test
    void generate_titleComponent_setsProperties() {
        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle("Test")
                .pageDescription("")
                .blocks(new ArrayList<>())
                .build();

        Map<String, Object> props = new HashMap<>();
        props.put("jcr:title", "Hello World");
        props.put("type", "h1");

        List<ComponentMapping> mappings = List.of(
                ComponentMapping.builder()
                        .targetComponent(AemComponentType.TITLE)
                        .componentName("title_0")
                        .properties(props)
                        .build()
        );

        AemPage page = agent.generate(analysis, mappings);

        var titleChild = page.getContent().getRoot().getChildren().get("title_0");
        assertNotNull(titleChild);
        assertEquals("core/wcm/components/title/v3/title", titleChild.getResourceType());
    }

    @Test
    void generate_imageComponent_setsFileReference() {
        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle("Test")
                .pageDescription("")
                .blocks(new ArrayList<>())
                .build();

        Map<String, Object> props = new HashMap<>();
        props.put("fileReference", "/content/dam/image.jpg");
        props.put("alt", "Test image");

        List<ComponentMapping> mappings = List.of(
                ComponentMapping.builder()
                        .targetComponent(AemComponentType.IMAGE)
                        .componentName("image_0")
                        .properties(props)
                        .build()
        );

        AemPage page = agent.generate(analysis, mappings);

        var imageChild = page.getContent().getRoot().getChildren().get("image_0");
        assertNotNull(imageChild);
        assertEquals("core/wcm/components/image/v3/image", imageChild.getResourceType());
    }

    @Test
    void generate_separatorComponent_createsSimpleComponent() {
        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle("Test")
                .pageDescription("")
                .blocks(new ArrayList<>())
                .build();

        List<ComponentMapping> mappings = List.of(
                ComponentMapping.builder()
                        .targetComponent(AemComponentType.SEPARATOR)
                        .componentName("separator_0")
                        .properties(new HashMap<>())
                        .build()
        );

        AemPage page = agent.generate(analysis, mappings);

        var sepChild = page.getContent().getRoot().getChildren().get("separator_0");
        assertNotNull(sepChild);
        assertEquals("core/wcm/components/separator/v1/separator", sepChild.getResourceType());
    }

    @Test
    void generate_emptyMappings_createsPageWithEmptyRoot() {
        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle("Empty")
                .pageDescription("")
                .blocks(new ArrayList<>())
                .build();

        AemPage page = agent.generate(analysis, new ArrayList<>());

        assertNotNull(page);
        assertTrue(page.getContent().getRoot().getChildren().isEmpty());
    }
}
