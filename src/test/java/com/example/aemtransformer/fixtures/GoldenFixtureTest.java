package com.example.aemtransformer.fixtures;

import com.example.aemtransformer.agent.AemGeneratorAgent;
import com.example.aemtransformer.agent.ComponentMapperAgent;
import com.example.aemtransformer.config.AppConfig;
import com.example.aemtransformer.model.AemPage;
import com.example.aemtransformer.model.ComponentMapping;
import com.example.aemtransformer.model.ContentAnalysis;
import com.example.aemtransformer.model.ContentBlock;
import com.example.aemtransformer.service.HtmlParserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GoldenFixtureTest {

    private final HtmlParserService htmlParserService = new HtmlParserService();
    private final ComponentMapperAgent componentMapperAgent = new ComponentMapperAgent();
    private final AemGeneratorAgent aemGeneratorAgent = new AemGeneratorAgent();
    private final ObjectMapper objectMapper = new AppConfig().objectMapper();

    @Test
    void sampleFixture_matchesExpectedJson() throws Exception {
        String html = readResource("fixtures/wp-sample.html");
        String expectedJson = readResource("fixtures/wp-sample-expected.json");

        List<ContentBlock> blocks = htmlParserService.parseHtml(html);
        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle("Sample Title")
                .pageDescription("Sample description")
                .blocks(blocks)
                .build();

        List<ComponentMapping> mappings = componentMapperAgent.mapComponents(analysis);
        AemPage page = aemGeneratorAgent.generate(analysis, mappings);

        JsonNode actualNode = objectMapper.readTree(objectMapper.writeValueAsBytes(page));
        JsonNode expectedNode = objectMapper.readTree(expectedJson);

        assertEquals(expectedNode, actualNode);
    }

    private String readResource(String path) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, "Missing test resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
