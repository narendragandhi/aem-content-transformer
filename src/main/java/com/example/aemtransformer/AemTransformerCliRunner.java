package com.example.aemtransformer;

import com.example.aemtransformer.agent.WordPressScraperAgent;
import com.example.aemtransformer.agent.ContentAnalyzerAgent;
import com.example.aemtransformer.agent.ComponentMapperAgent;
import com.example.aemtransformer.exception.ContentParsingException;
import com.example.aemtransformer.exception.TransformationException;
import com.example.aemtransformer.exception.ValidationException;
import com.example.aemtransformer.exception.WordPressApiException;
import com.example.aemtransformer.model.WordPressContent;
import com.example.aemtransformer.model.ContentAnalysis;
import com.example.aemtransformer.model.ComponentMapping;
import com.example.aemtransformer.model.AemPage;
import com.example.aemtransformer.service.AemOutputService;
import com.example.aemtransformer.util.ParsedUrl;
import com.example.aemtransformer.util.MappingToPageConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AemTransformerCliRunner implements CommandLineRunner {

    private final WordPressScraperAgent wordPressScraperAgent;
    private final ContentAnalyzerAgent contentAnalyzerAgent;
    private final ComponentMapperAgent componentMapperAgent;
    private final AemOutputService aemOutputService;

    @Override
    public void run(String... args) {
        if (args.length == 0) {
            log.info("Usage: java -jar <jarfile> <WordPress Article URL>");
            return;
        }

        String url = args[0];
        log.info("Starting transformation for URL: {}", url);

        try {
            ParsedUrl parsed = ParsedUrl.parse(url);
            log.debug("Parsed URL - base: {}, slug: {}, type: {}", parsed.baseUrl, parsed.slug, parsed.type);

            log.info("Fetching WordPress content...");
            WordPressContent wpContent = wordPressScraperAgent.scrapeBySlug(parsed.baseUrl, parsed.slug, parsed.type);

            log.info("Analyzing content: {}", wpContent.getTitleText());
            ContentAnalysis analysis = contentAnalyzerAgent.analyze(wpContent);

            log.info("Mapping {} content blocks to AEM components...", analysis.getBlocks().size());
            List<ComponentMapping> mappings = componentMapperAgent.mapComponents(analysis);

            log.info("Generating AEM page structure...");
            AemPage page = MappingToPageConverter.toAemPage(analysis, mappings);

            Path out = aemOutputService.writePage(page, parsed.slug);
            log.info("AEM page written successfully to: {}", out);

        } catch (ValidationException e) {
            log.error("Validation error: {}", e.getMessage());
        } catch (WordPressApiException e) {
            log.error("WordPress API error: {}", e.getMessage());
            if (e.isNotFound()) {
                log.info("Hint: Check that the URL points to an existing WordPress post or page");
            }
        } catch (ContentParsingException e) {
            log.error("Content parsing error: {}", e.getMessage());
        } catch (TransformationException e) {
            log.error("Transformation error: {}", e.getMessage());
        } catch (IOException e) {
            log.error("Failed to write AEM page: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during transformation: {}", e.getMessage(), e);
        }
    }
}
