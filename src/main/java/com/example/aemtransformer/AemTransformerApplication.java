package com.example.aemtransformer;

import com.example.aemtransformer.workflow.TransformationWorkflow;
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

    private final TransformationWorkflow workflow;

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

        TransformationResult result = workflow.execute(sourceUrl, contentId, contentType);

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

        int page = 1;
        int totalProcessed = 0;
        int successCount = 0;
        int failureCount = 0;

        while (page <= maxPages) {
            log.info("Processing page {} of {}...", page, contentType);

            try {
                var contents = "posts".equals(contentType) || "post".equals(contentType)
                        ? workflow.fetchAllPosts(sourceUrl, page, perPage)
                        : workflow.fetchAllPages(sourceUrl, page, perPage);

                if (contents.isEmpty()) {
                    log.info("No more {} found at page {}", contentType, page);
                    break;
                }

                for (var content : contents) {
                    totalProcessed++;
                    try {
                        log.info("[{}/batch] Processing: {}", totalProcessed, content.getTitleText());
                        TransformationResult result = workflow.execute(sourceUrl, content.getId(), contentType);

                        if (result.success()) {
                            successCount++;
                            log.info("[{}/batch] Success: {}", totalProcessed, result.outputPath());
                        } else {
                            failureCount++;
                            log.warn("[{}/batch] Failed: {}", totalProcessed, result.errorMessage());
                        }
                    } catch (Exception e) {
                        failureCount++;
                        log.error("[{}/batch] Error processing {}: {}",
                                totalProcessed, content.getTitleText(), e.getMessage());
                    }
                }

                page++;

            } catch (Exception e) {
                log.error("Error fetching {} page {}: {}", contentType, page, e.getMessage());
                break;
            }
        }

        log.info("Batch processing complete!");
        log.info("Total processed: {}, Success: {}, Failed: {}",
                totalProcessed, successCount, failureCount);

        if (failureCount > 0) {
            System.exit(1);
        }
    }

    private void printUsage() {
        System.out.println("""

            AEM Content Transformer - WordPress to AEM Core Components Migration

            Usage:
              transform <wordpress-url> <content-id> <type>   Transform a single post or page
              batch <wordpress-url> <type>                    Transform all posts or pages
              help                                            Show this help message

            Arguments:
              wordpress-url   Base URL of the WordPress site (e.g., https://example.com)
              content-id      ID of the post or page to transform
              type            Content type: 'post' or 'page'

            Environment Variables:
              OLLAMA_BASE_URL     Ollama server URL (default: http://localhost:11434)
              OLLAMA_MODEL        Model to use (default: llama3.2)
              AEM_OUTPUT_PATH     Output directory (default: ./output)
              AEM_SITE_PATH       AEM site path (default: /content/mysite)

            Examples:
              java -jar aem-transformer.jar transform https://myblog.com 123 post
              java -jar aem-transformer.jar transform https://mysite.com 45 page

            """);
    }
}
