package com.example.aemtransformer.service;

import com.example.aemtransformer.model.AemPage;
import com.example.aemtransformer.model.AemPage.ComponentNode;
import com.example.aemtransformer.model.ContentAnalysis;
import com.example.aemtransformer.model.ParityReportEntry;
import com.example.aemtransformer.model.WordPressContent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Bead for Automated Quality Assurance.
 * Calculates a "Trust Score" by comparing source and target structures.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParityReportService {

    private final ObjectMapper objectMapper;

    @Value("${migration.parity.enabled:true}")
    private boolean enabled;

    @Value("${migration.parity.threshold:0.8}")
    private double trustThreshold;

    @Value("${migration.parity.filename:parity.jsonl}")
    private String parityFilename;

    @Value("${aem.output-path:./output}")
    private String outputPath;

    public void append(WordPressContent content, ContentAnalysis analysis, AemPage page, String outputPathValue) {
        if (!enabled || content == null || analysis == null || page == null || page.getContent() == null) {
            return;
        }

        String title = content.getTitleText();
        String aemTitle = page.getContent().getTitle();
        boolean titleMatch = Objects.equals(normalize(title), normalize(aemTitle));

        int sourceTagCount = content.getTags() != null ? content.getTags().size() : 0;
        int aemTagCount = page.getContent().getTags() != null ? page.getContent().getTags().length : 0;

        int sourceImageCount = analysis.getTotalImages();
        int aemImageCount = countImageComponents(page.getContent().getRoot());

        int sourceTextLength = safeLength(content.getContentHtml());
        int aemTextLength = countTextLength(page.getContent().getRoot());

        // PRIME TIME: Trust Score Calculation
        double trustScore = calculateTrustScore(titleMatch, sourceImageCount, aemImageCount, sourceTextLength, aemTextLength);
        boolean isComplete = trustScore >= trustThreshold;

        ParityReportEntry entry = new ParityReportEntry(
                buildKey(content),
                content.getSlug(),
                titleMatch,
                sourceTagCount,
                aemTagCount,
                sourceImageCount,
                aemImageCount,
                sourceTextLength,
                aemTextLength,
                trustScore,
                isComplete,
                outputPathValue,
                Instant.now()
        );

        writeEntry(entry);
    }

    private double calculateTrustScore(boolean titleMatch, int srcImg, int aemImg, int srcLen, int aemLen) {
        double score = 0.0;
        
        // Title weights 20%
        if (titleMatch) score += 0.2;
        
        // Image parity weights 40%
        if (srcImg == 0 && aemImg == 0) {
            score += 0.4;
        } else if (srcImg > 0) {
            double ratio = (double) Math.min(srcImg, aemImg) / Math.max(srcImg, aemImg);
            score += (0.4 * ratio);
        }

        // Content volume weights 40%
        if (srcLen > 0) {
            // AEM content is usually shorter due to stripping HTML boilerplate
            // We use a 0.7 to 1.3 tolerance window
            double ratio = (double) aemLen / srcLen;
            if (ratio >= 0.6 && ratio <= 1.4) {
                score += 0.4;
            } else {
                score += (0.4 * Math.min(ratio, 1.0));
            }
        }

        return Math.min(1.0, score);
    }

    private void writeEntry(ParityReportEntry entry) {
        try {
            Path path = Path.of(outputPath, parityFilename);
            Files.createDirectories(path.getParent());
            String line = objectMapper.writeValueAsString(entry) + System.lineSeparator();
            Files.writeString(path, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("Failed to write parity report: {}", e.getMessage());
        }
    }

    private int countImageComponents(ComponentNode root) {
        if (root == null) {
            return 0;
        }
        int count = 0;
        Deque<ComponentNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            ComponentNode node = stack.pop();
            // Match common AEM image resource types
            if (node.getResourceType() != null && node.getResourceType().contains("image")) {
                count++;
            }
            if (node.getChildren() != null) {
                for (ComponentNode child : node.getChildren().values()) {
                    stack.push(child);
                }
            }
        }
        return count;
    }

    private int countTextLength(ComponentNode root) {
        if (root == null) {
            return 0;
        }
        int length = 0;
        Deque<ComponentNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            ComponentNode node = stack.pop();
            if (node.getProperties() != null) {
                // Sum up text properties from common components
                if (node.getProperties().containsKey("text")) {
                    length += safeLength(node.getProperties().get("text").toString());
                }
                if (node.getProperties().containsKey("jcr:title")) {
                    length += safeLength(node.getProperties().get("jcr:title").toString());
                }
            }
            if (node.getChildren() != null) {
                for (ComponentNode child : node.getChildren().values()) {
                    stack.push(child);
                }
            }
        }
        return length;
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildKey(WordPressContent content) {
        return String.join("|",
                Objects.toString(content.getId(), ""),
                Objects.toString(content.getSlug(), "")
        );
    }
}
