package com.example.aemtransformer;

import com.example.aemtransformer.agent.AemGeneratorAgent;
import com.example.aemtransformer.agent.ComponentMapperAgent;
import com.example.aemtransformer.agent.ContentAnalyzerAgent;
import com.example.aemtransformer.agent.WordPressScraperAgent;
import com.example.aemtransformer.model.AemPage;
import com.example.aemtransformer.model.ComponentMapping;
import com.example.aemtransformer.model.ContentAnalysis;
import com.example.aemtransformer.model.WordPressContent;
import com.example.aemtransformer.service.AemOutputService;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AemTransformerIT {

    private static final Path OUTPUT_DIR = createTempDir();

    @Autowired
    private WordPressScraperAgent wordPressScraperAgent;
    @Autowired
    private ContentAnalyzerAgent contentAnalyzerAgent;
    @Autowired
    private ComponentMapperAgent componentMapperAgent;
    @Autowired
    private AemGeneratorAgent aemGeneratorAgent;
    @Autowired
    private AemOutputService aemOutputService;

    private WireMockServer wireMockServer;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("aem.output-path", () -> OUTPUT_DIR.toString());
    }

    @BeforeAll
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());

        stubFor(get(urlPathEqualTo("/wp-json/wp/v2/posts"))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("per_page", equalTo("1"))
                .withQueryParam("_embed", equalTo("true"))
                .willReturn(okJson("""
                        [
                          {
                            "id": 123,
                            "slug": "hello-world",
                            "title": { "rendered": "Hello World" },
                            "content": { "rendered": "<p>Test content</p>" },
                            "excerpt": { "rendered": "<p>Test content</p>" }
                          }
                        ]
                        """)));
    }

    @AfterAll
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    public void testFullPipeline() {
        String baseUrl = wireMockServer.baseUrl();
        List<WordPressContent> posts = wordPressScraperAgent.scrapeAllPosts(baseUrl, 1, 1);
        assertFalse(posts.isEmpty(), "Should have at least one post available");

        WordPressContent wpContent = posts.get(0);
        String slug = wpContent.getSlug();
        assertNotNull(slug, "Post should have a slug");
        assertNotNull(wpContent, "WordPress content should not be null");
        ContentAnalysis analysis = contentAnalyzerAgent.analyze(wpContent);
        assertNotNull(analysis, "Content analysis should not be null");
        List<ComponentMapping> mappings = componentMapperAgent.mapComponents(analysis);
        assertNotNull(mappings, "Component mappings should not be null");
        assertFalse(mappings.isEmpty(), "Component mappings should not be empty");
        AemPage page = aemGeneratorAgent.generate(analysis, mappings);
        try {
            Path out = aemOutputService.writePage(page, slug);
            assertNotNull(out, "AEM output path should not be null");
            assertTrue(Files.exists(out), "Output file should exist");
        } catch (java.io.IOException e) {
            fail("Failed to write AEM page: " + e.getMessage());
        }
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("aem-transformer-it-");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create temp output directory", e);
        }
    }
}
