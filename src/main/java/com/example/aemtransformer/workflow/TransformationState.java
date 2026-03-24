package com.example.aemtransformer.workflow;

import com.example.aemtransformer.model.AemPage;
import com.example.aemtransformer.model.ComponentMapping;
import com.example.aemtransformer.model.ContentAnalysis;
import com.example.aemtransformer.model.TagMapping;
import com.example.aemtransformer.model.WordPressContent;
import lombok.Getter;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * State object for the content transformation workflow.
 * Maintains state as it flows through the LangGraph4j graph.
 */
@Getter
public class TransformationState extends AgentState {

    public static final String SOURCE_URL_KEY = "sourceUrl";
    public static final String CONTENT_ID_KEY = "contentId";
    public static final String CONTENT_TYPE_KEY = "contentType";
    public static final String WORDPRESS_CONTENT_KEY = "wordPressContent";
    public static final String CONTENT_ANALYSIS_KEY = "contentAnalysis";
    public static final String COMPONENT_MAPPINGS_KEY = "componentMappings";
    public static final String AEM_PAGE_KEY = "aemPage";
    public static final String TAG_MAPPINGS_KEY = "tagMappings";
    public static final String OUTPUT_PATH_KEY = "outputPath";
    public static final String CURRENT_PHASE_KEY = "currentPhase";
    public static final String ERRORS_KEY = "errors";
    public static final String RETRY_COUNT_KEY = "retryCount";
    public static final String TRUST_SCORE_KEY = "trustScore";

    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            Map.entry(SOURCE_URL_KEY, Channel.of((Supplier<String>) () -> "")),
            Map.entry(CONTENT_ID_KEY, Channel.of((Supplier<Long>) () -> -1L)),
            Map.entry(CONTENT_TYPE_KEY, Channel.of((Supplier<String>) () -> "post")),
            Map.entry(WORDPRESS_CONTENT_KEY, Channel.of((Supplier<WordPressContent>) WordPressContent::new)),
            Map.entry(CONTENT_ANALYSIS_KEY, Channel.of((Supplier<ContentAnalysis>) ContentAnalysis::new)),
            Map.entry(COMPONENT_MAPPINGS_KEY, Channel.of((Supplier<List<ComponentMapping>>) ArrayList::new)),
            Map.entry(AEM_PAGE_KEY, Channel.of((Supplier<AemPage>) AemPage::new)),
            Map.entry(TAG_MAPPINGS_KEY, Channel.of((Supplier<List<TagMapping>>) ArrayList::new)),
            Map.entry(OUTPUT_PATH_KEY, Channel.of((Supplier<String>) () -> "")),
            Map.entry(CURRENT_PHASE_KEY, Channel.of((Supplier<String>) () -> "init")),
            Map.entry(ERRORS_KEY, Channel.of((Supplier<List<String>>) ArrayList::new)),
            Map.entry(RETRY_COUNT_KEY, Channel.of((Supplier<Integer>) () -> 0)),
            Map.entry(TRUST_SCORE_KEY, Channel.of((Supplier<Double>) () -> 0.0))
    );

    public TransformationState(Map<String, Object> initData) {
        super(initData);
    }

    public String getSourceUrl() {
        return Optional.ofNullable((String) value(SOURCE_URL_KEY).orElse(null)).orElse("");
    }

    public Long getContentId() {
        Object val = value(CONTENT_ID_KEY).orElse(null);
        if (val instanceof Number) {
            long id = ((Number) val).longValue();
            return id > 0 ? id : null;
        }
        return null;
    }

    public String getContentType() {
        return Optional.ofNullable((String) value(CONTENT_TYPE_KEY).orElse(null)).orElse("post");
    }

    public WordPressContent getWordPressContent() {
        return (WordPressContent) value(WORDPRESS_CONTENT_KEY).orElse(null);
    }

    public ContentAnalysis getContentAnalysis() {
        return (ContentAnalysis) value(CONTENT_ANALYSIS_KEY).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public List<ComponentMapping> getComponentMappings() {
        Object val = value(COMPONENT_MAPPINGS_KEY).orElse(null);
        if (val instanceof List) {
            return (List<ComponentMapping>) val;
        }
        return new ArrayList<>();
    }

    public AemPage getAemPage() {
        return (AemPage) value(AEM_PAGE_KEY).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public List<TagMapping> getTagMappings() {
        Object val = value(TAG_MAPPINGS_KEY).orElse(null);
        if (val instanceof List) {
            return (List<TagMapping>) val;
        }
        return new ArrayList<>();
    }

    public String getOutputPath() {
        return Optional.ofNullable((String) value(OUTPUT_PATH_KEY).orElse(null)).orElse("");
    }

    public String getCurrentPhase() {
        return Optional.ofNullable((String) value(CURRENT_PHASE_KEY).orElse(null)).orElse("init");
    }

    @SuppressWarnings("unchecked")
    public List<String> getErrors() {
        Object val = value(ERRORS_KEY).orElse(null);
        if (val instanceof List) {
            return (List<String>) val;
        }
        return new ArrayList<>();
    }

    public int getRetryCount() {
        Object val = value(RETRY_COUNT_KEY).orElse(null);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return 0;
    }

    public double getTrustScore() {
        Object val = value(TRUST_SCORE_KEY).orElse(null);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return 0.0;
    }

    public boolean hasErrors() {
        List<String> errors = getErrors();
        return errors != null && !errors.isEmpty();
    }
}
