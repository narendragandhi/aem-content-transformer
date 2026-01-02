package com.example.aemtransformer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents a parsed content block from WordPress HTML.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentBlock {

    public enum BlockType {
        HEADING,
        PARAGRAPH,
        IMAGE,
        LIST,
        QUOTE,
        CODE,
        TABLE,
        EMBED,
        GALLERY,
        SEPARATOR,
        UNKNOWN
    }

    private BlockType type;

    private String content;

    private String rawHtml;

    private int order;

    private Map<String, String> attributes;

    private List<ContentBlock> children;

    private int headingLevel;

    private String imageUrl;

    private String imageAlt;

    private String imageCaption;

    private List<String> listItems;

    private boolean isOrdered;
}
