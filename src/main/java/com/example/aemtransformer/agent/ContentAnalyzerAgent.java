package com.example.aemtransformer.agent;

import com.example.aemtransformer.model.ContentAnalysis;
import com.example.aemtransformer.model.ContentBlock;
import com.example.aemtransformer.model.WordPressContent;
import com.example.aemtransformer.service.HtmlParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent responsible for analyzing WordPress content structure.
 * Uses LLM for semantic understanding and HTML parser for structural analysis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentAnalyzerAgent {

    private final HtmlParserService htmlParserService;
    private final ChatModel chatModel;

    /**
     * Analyzes WordPress content and extracts structured blocks.
     */
    public ContentAnalysis analyze(WordPressContent content) {
        log.info("Analyzing content: {}", content.getTitleText());

        List<ContentBlock> blocks = htmlParserService.parseHtml(content.getContentHtml());

        ContentAnalysis analysis = ContentAnalysis.builder()
                .pageTitle(content.getTitleText())
                .pageDescription(cleanHtml(content.getExcerptText()))
                .blocks(blocks)
                .totalImages(countBlocksByType(blocks, ContentBlock.BlockType.IMAGE))
                .totalHeadings(countBlocksByType(blocks, ContentBlock.BlockType.HEADING))
                .hasGallery(hasBlockType(blocks, ContentBlock.BlockType.GALLERY))
                .hasEmbeds(hasBlockType(blocks, ContentBlock.BlockType.EMBED))
                .build();

        String summary = generateContentSummary(content);
        analysis.setContentSummary(summary);

        log.info("Analysis complete: {} blocks, {} images, {} headings",
                blocks.size(), analysis.getTotalImages(), analysis.getTotalHeadings());

        return analysis;
    }

    /**
     * Uses LLM to generate a content summary and extract keywords.
     */
    private String generateContentSummary(WordPressContent content) {
        try {
            ChatClient chatClient = ChatClient.create(chatModel);

            String prompt = """
                    Analyze the following content and provide a brief 2-3 sentence summary.
                    Focus on the main topic and key points.

                    Title: %s

                    Content:
                    %s

                    Provide only the summary, no additional formatting.
                    """.formatted(content.getTitleText(), cleanHtml(content.getContentHtml()));

            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Failed to generate content summary via LLM: {}", e.getMessage());
            return content.getExcerptText();
        }
    }

    private String cleanHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int countBlocksByType(List<ContentBlock> blocks, ContentBlock.BlockType type) {
        return (int) blocks.stream()
                .filter(b -> b.getType() == type)
                .count();
    }

    private boolean hasBlockType(List<ContentBlock> blocks, ContentBlock.BlockType type) {
        return blocks.stream().anyMatch(b -> b.getType() == type);
    }
}
