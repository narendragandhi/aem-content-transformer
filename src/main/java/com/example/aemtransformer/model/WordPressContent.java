package com.example.aemtransformer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents content fetched from WordPress REST API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WordPressContent {

    private Long id;

    private String slug;

    private String status;

    private String type;

    private String link;

    private Title title;

    private Content content;

    private Excerpt excerpt;

    @JsonProperty("date")
    private LocalDateTime publishedDate;

    @JsonProperty("modified")
    private LocalDateTime modifiedDate;

    @JsonProperty("featured_media")
    private Long featuredMedia;

    private List<Long> categories;

    private List<Long> tags;

    private Map<String, Object> meta;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Title {
        private String rendered;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Content {
        private String rendered;
        @JsonProperty("protected")
        private boolean isProtected;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Excerpt {
        private String rendered;
        @JsonProperty("protected")
        private boolean isProtected;
    }

    public String getTitleText() {
        return title != null ? title.getRendered() : "";
    }

    public String getContentHtml() {
        return content != null ? content.getRendered() : "";
    }

    public String getExcerptText() {
        return excerpt != null ? excerpt.getRendered() : "";
    }
}
