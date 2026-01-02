package com.example.aemtransformer.workflow;

import com.example.aemtransformer.model.WordPressContent;
import com.example.aemtransformer.service.WordPressApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * LangGraph4j workflow definition for WordPress to AEM content transformation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransformationWorkflow {

    private final WorkflowNodes nodes;
    private final WordPressApiService wordPressApiService;

    private CompiledGraph<TransformationState> compiledGraph;

    @PostConstruct
    public void init() throws Exception {
        log.info("Initializing transformation workflow graph");

        StateGraph<TransformationState> workflow = new StateGraph<>(
                TransformationState.SCHEMA,
                TransformationState::new
        );

        workflow
                .addNode("scrape", node_async(nodes::scrapeNode))
                .addNode("analyze", node_async(nodes::analyzeNode))
                .addNode("transform", node_async(nodes::transformNode))
                .addNode("generate", node_async(nodes::generateNode))
                .addNode("output", node_async(nodes::outputNode));

        workflow
                .addEdge(START, "scrape")
                .addEdge("scrape", "analyze")
                .addEdge("analyze", "transform")
                .addEdge("transform", "generate")
                .addConditionalEdges("generate",
                        state -> CompletableFuture.completedFuture(nodes.routeAfterGenerate(state)),
                        Map.of(
                                "success", "output",
                                "retry", "transform",
                                "fail", END
                        ))
                .addEdge("output", END);

        this.compiledGraph = workflow.compile();
        log.info("Workflow graph compiled successfully");
    }

    /**
     * Executes the transformation workflow for a single piece of content.
     */
    public TransformationResult execute(String sourceUrl, Long contentId, String contentType) {
        log.info("Starting transformation workflow for {} {} from {}",
                contentType, contentId, sourceUrl);

        Map<String, Object> initialState = new HashMap<>();
        initialState.put(TransformationState.SOURCE_URL_KEY, sourceUrl);
        initialState.put(TransformationState.CONTENT_ID_KEY, contentId);
        initialState.put(TransformationState.CONTENT_TYPE_KEY, contentType);

        TransformationState finalState = null;

        try {
            for (var nodeOutput : compiledGraph.stream(initialState)) {
                log.debug("Node output: {}", nodeOutput.node());
                finalState = nodeOutput.state();
            }

            if (finalState == null) {
                return TransformationResult.failure("Workflow produced no output");
            }

            if (finalState.hasErrors()) {
                return TransformationResult.failure(
                        String.join("; ", finalState.getErrors()),
                        finalState
                );
            }

            return TransformationResult.success(
                    finalState.getOutputPath(),
                    finalState
            );

        } catch (Exception e) {
            log.error("Workflow execution failed", e);
            return TransformationResult.failure("Workflow error: " + e.getMessage());
        }
    }

    /**
     * Fetches all posts from the WordPress site with pagination.
     */
    public List<WordPressContent> fetchAllPosts(String sourceUrl, int page, int perPage) {
        log.debug("Fetching posts from {} - page {}, perPage {}", sourceUrl, page, perPage);
        return wordPressApiService.fetchAllPosts(sourceUrl, page, perPage);
    }

    /**
     * Fetches all pages from the WordPress site with pagination.
     */
    public List<WordPressContent> fetchAllPages(String sourceUrl, int page, int perPage) {
        log.debug("Fetching pages from {} - page {}, perPage {}", sourceUrl, page, perPage);
        return wordPressApiService.fetchAllPages(sourceUrl, page, perPage);
    }

    /**
     * Result of a transformation workflow execution.
     */
    public record TransformationResult(
            boolean success,
            String outputPath,
            String errorMessage,
            TransformationState finalState
    ) {
        public static TransformationResult success(String outputPath, TransformationState state) {
            return new TransformationResult(true, outputPath, null, state);
        }

        public static TransformationResult failure(String errorMessage) {
            return new TransformationResult(false, null, errorMessage, null);
        }

        public static TransformationResult failure(String errorMessage, TransformationState state) {
            return new TransformationResult(false, null, errorMessage, state);
        }
    }
}
