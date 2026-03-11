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

@Slf4j
@Service
@RequiredArgsConstructor
public class ParityReportService {

    private final ObjectMapper objectMapper;

    @Value("${migration.parity.enabled:true}")
    private boolean enabled;

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
                outputPathValue,
                Instant.now()
        );

        writeEntry(entry);
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
            if ("core/wcm/components/image/v3/image".equals(node.getResourceType())) {
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
            if (node.getProperties() != null && node.getProperties().containsKey("text")) {
                Object text = node.getProperties().get("text");
                length += safeLength(text != null ? text.toString() : null);
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
