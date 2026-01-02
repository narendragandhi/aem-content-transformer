package com.example.aemtransformer.util;

import com.example.aemtransformer.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ParsedUrlTest {

    @Test
    void parse_validNewsUrl_extractsSlugAndType() {
        ParsedUrl parsed = ParsedUrl.parse("https://wordpress.org/news/2025/12/my-post/");

        assertEquals("https://wordpress.org/news", parsed.baseUrl);
        assertEquals("my-post", parsed.slug);
        assertEquals("posts", parsed.type);
    }

    @Test
    void parse_validArticlesUrl_extractsSlugAndType() {
        ParsedUrl parsed = ParsedUrl.parse("https://example.com/articles/test-article/");

        assertEquals("https://example.com/articles", parsed.baseUrl);
        assertEquals("test-article", parsed.slug);
        assertEquals("articles", parsed.type);
    }

    @Test
    void parse_urlWithQueryParams_stripsQueryParams() {
        ParsedUrl parsed = ParsedUrl.parse("https://example.com/news/my-post/?utm_source=test");

        assertEquals("my-post", parsed.slug);
    }

    @Test
    void parse_urlWithHash_stripsHash() {
        ParsedUrl parsed = ParsedUrl.parse("https://example.com/news/my-post/#section1");

        assertEquals("my-post", parsed.slug);
    }

    @Test
    void parse_nullUrl_throwsValidationException() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            ParsedUrl.parse(null);
        });

        assertTrue(exception.getMessage().contains("null or empty"));
    }

    @Test
    void parse_emptyUrl_throwsValidationException() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            ParsedUrl.parse("");
        });

        assertTrue(exception.getMessage().contains("null or empty"));
    }

    @Test
    void parse_invalidProtocol_throwsValidationException() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            ParsedUrl.parse("ftp://example.com/news/post/");
        });

        assertTrue(exception.getMessage().contains("HTTP/HTTPS"));
    }

    @Test
    void parse_malformedUrl_throwsValidationException() {
        ValidationException exception = assertThrows(ValidationException.class, () -> {
            ParsedUrl.parse("not-a-valid-url");
        });

        assertTrue(exception.getMessage().contains("Invalid URL"));
    }

    @Test
    void parse_pathTraversalAttempt_extractsOnlyLastSegment() {
        // Path traversal is mitigated by extracting only the last segment
        ParsedUrl parsed = ParsedUrl.parse("https://example.com/news/2025/01/safe-slug/");
        assertEquals("safe-slug", parsed.slug);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/news/invalid slug",
            "https://example.com/news/slug<>with-brackets",
            "https://example.com/news/slug'with'quotes"
    })
    void parse_slugWithInvalidChars_throwsValidationException(String url) {
        assertThrows(ValidationException.class, () -> {
            ParsedUrl.parse(url);
        });
    }

    @Test
    void constructor_validInputs_createsInstance() {
        ParsedUrl parsed = new ParsedUrl("https://example.com", "valid-slug", "posts");

        assertEquals("https://example.com", parsed.baseUrl);
        assertEquals("valid-slug", parsed.slug);
        assertEquals("posts", parsed.type);
    }

    @Test
    void constructor_invalidSlug_throwsValidationException() {
        assertThrows(ValidationException.class, () -> {
            new ParsedUrl("https://example.com", "invalid slug with spaces", "posts");
        });
    }

    @Test
    void constructor_emptySlug_throwsValidationException() {
        assertThrows(ValidationException.class, () -> {
            new ParsedUrl("https://example.com", "", "posts");
        });
    }

    @Test
    void constructor_invalidType_throwsValidationException() {
        assertThrows(ValidationException.class, () -> {
            new ParsedUrl("https://example.com", "valid-slug", "invalid-type");
        });
    }

    @Test
    void constructor_nullType_defaultsToPosts() {
        ParsedUrl parsed = new ParsedUrl("https://example.com", "valid-slug", null);

        assertEquals("posts", parsed.type);
    }

    @Test
    void parse_veryLongUrl_throwsValidationException() {
        String longUrl = "https://example.com/news/" + "a".repeat(3000) + "/";

        ValidationException exception = assertThrows(ValidationException.class, () -> {
            ParsedUrl.parse(longUrl);
        });

        assertTrue(exception.getMessage().contains("maximum length"));
    }
}
