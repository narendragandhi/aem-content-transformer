package com.example.aemtransformer;

import com.example.aemtransformer.service.BatchMigrationService;
import com.example.aemtransformer.service.TransformationService;
import com.example.aemtransformer.workflow.TransformationWorkflow.TransformationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Main application for WordPress to AEM content transformation.
 * Uses LangGraph4j for workflow orchestration and Embabel-inspired agents.
 */
@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class AemTransformerApplication {

    private final TransformationService transformationService;
    private final BatchMigrationService batchMigrationService;

    public static void main(String[] args) {
        SpringApplication.run(AemTransformerApplication.class, args);
    }

    @Bean
    public CommandLineRunner runner() {
        return args -> {
            if (args.length < 2) {
                printUsage();
                return;
            }

            String command = args[0];

            switch (command) {
                case "transform" -> handleTransform(args);
                case "transform-url" -> handleTransformUrl(args);
                case "batch" -> handleBatch(args);
                case "help" -> printUsage();
                default -> {
                    log.error("Unknown command: {}", command);
                    printUsage();
                }
            }
        };
    }

    private void handleTransform(String[] args) {
        if (args.length < 4) {
            log.error("Usage: transform <wordpress-url> <content-id> <type>");
            return;
        }

        String sourceUrl = args[1];
        Long contentId = Long.parseLong(args[2]);
        String contentType = args[3];

        log.info("Starting transformation of {} {} from {}", contentType, contentId, sourceUrl);

        TransformationResult result = transformationService.transformById(sourceUrl, contentId, contentType);

        if (result.success()) {
            log.info("Transformation completed successfully!");
            log.info("Output written to: {}", result.outputPath());
        } else {
            log.error("Transformation failed: {}", result.errorMessage());
            System.exit(1);
        }
    }

    private void handleBatch(String[] args) {
        if (args.length < 3) {
            log.error("Usage: batch <wordpress-url> <type>");
            return;
        }

        String sourceUrl = args[1];
        String contentType = args[2];
        int perPage = 10;
        int maxPages = args.length > 3 ? Integer.parseInt(args[3]) : 100;

        log.info("Starting batch processing of {} from {}", contentType, sourceUrl);

        BatchMigrationService.BatchResult result =
                batchMigrationService.runBatch(sourceUrl, contentType, perPage, maxPages);

        log.info("Batch processing complete!");
        log.info("Total processed: {}, Success: {}, Failed: {}, Skipped: {}",
                result.totalProcessed(), result.successCount(), result.failureCount(), result.skippedCount());

        if (result.failureCount() > 0) {
            System.exit(1);
        }
    }

    private void printUsage() {
        System.out.println("""

            AEM Content Transformer - WordPress to AEM Core Components Migration

            Usage:
              transform <wordpress-url> <content-id> <type>   Transform a single post or page
              transform-url <wordpress-article-url>          Transform a single post or page by URL
              batch <wordpress-url> <type>                    Transform all posts or pages
              help                                            Show this help message

            Arguments:
              wordpress-url   Base URL of the WordPress site (e.g., https://example.com)
              content-id      ID of the post or page to transform
              type            Content type: 'post' or 'page'
              wordpress-article-url  Full URL of a WordPress post or page

            Environment Variables:
              OLLAMA_BASE_URL     Ollama server URL (default: http://localhost:11434)
              OLLAMA_MODEL        Model to use (default: llama3.2)
              AEM_OUTPUT_PATH     Output directory (default: ./output)
              AEM_SITE_PATH       AEM site path (default: /content/mysite)

            Examples:
              java -jar aem-transformer.jar transform https://myblog.com 123 post
              java -jar aem-transformer.jar transform https://mysite.com 45 page
              java -jar aem-transformer.jar transform-url https://example.com/news/my-post/

            """);
    }

    private void handleTransformUrl(String[] args) {
        if (args.length < 2) {
            log.error("Usage: transform-url <wordpress-article-url>");
            return;
        }

        String url = args[1];
        log.info("Starting transformation for URL: {}", url);

        try {
            var outputPath = transformationService.transformByUrl(url);
            log.info("Transformation completed successfully!");
            log.info("Output written to: {}", outputPath);
        } catch (Exception e) {
            log.error("Transformation failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
