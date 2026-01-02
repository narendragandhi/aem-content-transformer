package com.example.aemtransformer;

import com.example.aemtransformer.agent.WordPressScraperAgent;
import com.example.aemtransformer.agent.ContentAnalyzerAgent;
import com.example.aemtransformer.agent.ComponentMapperAgent;
import com.example.aemtransformer.model.WordPressContent;
import com.example.aemtransformer.model.ContentAnalysis;
import com.example.aemtransformer.model.ComponentMapping;
import com.example.aemtransformer.model.AemPage;
import com.example.aemtransformer.service.AemOutputService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;
import java.nio.file.Path;
import com.example.aemtransformer.util.MappingToPageConverter;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AemTransformerIntegrationTest {
        @Autowired
        private com.example.aemtransformer.service.WordPressApiService wordPressApiService;
    @Autowired
    private WordPressScraperAgent wordPressScraperAgent;
    @Autowired
    private ContentAnalyzerAgent contentAnalyzerAgent;
    @Autowired
    private ComponentMapperAgent componentMapperAgent;
    @Autowired
    private AemOutputService aemOutputService;

    @Test
    public void testFullPipeline() {
        // Force correct API path for test
        try {
            java.lang.reflect.Field apiPathField = wordPressApiService.getClass().getDeclaredField("apiPath");
            apiPathField.setAccessible(true);
            apiPathField.set(wordPressApiService, "/wp-json/wp/v2");
        } catch (Exception e) {
            throw new RuntimeException("Failed to set apiPath for test", e);
        }

        // Use wordpress.org/news - fetch the first available post dynamically
        String baseUrl = "https://wordpress.org/news";
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
        AemPage page = MappingToPageConverter.toAemPage(analysis, mappings);
        try {
            Path out = aemOutputService.writePage(page, slug);
            assertNotNull(out, "AEM output path should not be null");
            System.out.println("AEM page written to: " + out.toString());
        } catch (java.io.IOException e) {
            fail("Failed to write AEM page: " + e.getMessage());
        }
    }
}
