package com.example.aemtransformer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Result of content analysis phase - structured representation of WordPress content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAnalysis implements Serializable {

    private static final long serialVersionUID = 1L;

    private String pageTitle;

    private String pageDescription;

    @Builder.Default
    private List<ContentBlock> blocks = new ArrayList<>();

    private Map<String, Object> metadata;

    private String primaryCategory;

    private List<String> extractedKeywords;

    private String contentSummary;

    private boolean hasGallery;

    private boolean hasEmbeds;

    private int totalImages;

    private int totalHeadings;

    public void addBlock(ContentBlock block) {
        if (blocks == null) {
            blocks = new ArrayList<>();
        }
        blocks.add(block);
    }
}
