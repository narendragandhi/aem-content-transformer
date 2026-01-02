package com.example.aemtransformer.util;

import com.example.aemtransformer.exception.ValidationException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

public class ParsedUrl {

    private static final Pattern SAFE_SLUG_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_SLUG_LENGTH = 200;

    public final String baseUrl;
    public final String slug;
    public final String type;

    public ParsedUrl(String baseUrl, String slug, String type) {
        this.baseUrl = validateBaseUrl(baseUrl);
        this.slug = validateSlug(slug);
        this.type = validateType(type);
    }

    public static ParsedUrl parse(String url) {
        if (url == null || url.isBlank()) {
            throw new ValidationException("URL cannot be null or empty", "url");
        }

        if (url.length() > MAX_URL_LENGTH) {
            throw new ValidationException("URL exceeds maximum length of " + MAX_URL_LENGTH, "url", url.length());
        }

        // Validate URL format
        try {
            URL parsed = new URL(url);
            String protocol = parsed.getProtocol().toLowerCase();
            if (!protocol.equals("http") && !protocol.equals("https")) {
                throw new ValidationException("Only HTTP/HTTPS URLs are allowed", "url", protocol);
            }
        } catch (MalformedURLException e) {
            throw new ValidationException("Invalid URL format", "url", url);
        }

        // Support both /articles/SLUG/ and /news/SLUG/ and generic /TYPE/SLUG/ formats
        String base;
        String slug;
        String type = "posts";
        String[] knownTypes = {"articles", "news", "posts"};

        for (String t : knownTypes) {
            String marker = "/" + t + "/";
            int idx = url.indexOf(marker);
            if (idx != -1) {
                base = url.substring(0, idx + marker.length() - 1);
                slug = extractSlug(url.substring(idx + marker.length()));
                type = t.equals("news") ? "posts" : t;
                return new ParsedUrl(base, slug, type);
            }
        }

        // fallback: try to parse last path segment as slug
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash > 7) {
            base = url.substring(0, lastSlash);
            slug = extractSlug(url.substring(lastSlash + 1));
            return new ParsedUrl(base, slug, type);
        }

        throw new ValidationException("Cannot extract slug from URL", "url", url);
    }

    private static String extractSlug(String rawSlug) {
        // Remove trailing slashes and query parameters
        String slug = rawSlug.replaceAll("/$", "");
        int queryIndex = slug.indexOf('?');
        if (queryIndex != -1) {
            slug = slug.substring(0, queryIndex);
        }
        int hashIndex = slug.indexOf('#');
        if (hashIndex != -1) {
            slug = slug.substring(0, hashIndex);
        }

        // Handle date-based URL paths (e.g., /2025/12/10/slug/)
        String[] parts = slug.split("/");
        if (parts.length > 0) {
            slug = parts[parts.length - 1];
        }

        return slug;
    }

    private static String validateBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ValidationException("Base URL cannot be null or empty", "baseUrl");
        }

        try {
            URL parsed = new URL(baseUrl);
            String protocol = parsed.getProtocol().toLowerCase();
            if (!protocol.equals("http") && !protocol.equals("https")) {
                throw new ValidationException("Only HTTP/HTTPS URLs are allowed", "baseUrl", protocol);
            }
        } catch (MalformedURLException e) {
            throw new ValidationException("Invalid base URL format", "baseUrl", baseUrl);
        }

        return baseUrl.replaceAll("/$", "");
    }

    private static String validateSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new ValidationException("Slug cannot be null or empty", "slug");
        }

        if (slug.length() > MAX_SLUG_LENGTH) {
            throw new ValidationException("Slug exceeds maximum length of " + MAX_SLUG_LENGTH, "slug", slug.length());
        }

        // Allow alphanumeric, hyphens, and underscores only
        if (!SAFE_SLUG_PATTERN.matcher(slug).matches()) {
            throw new ValidationException("Slug contains invalid characters (only alphanumeric, hyphens, underscores allowed)", "slug", slug);
        }

        return slug;
    }

    private static String validateType(String type) {
        if (type == null || type.isBlank()) {
            return "posts"; // Default type
        }

        String normalized = type.toLowerCase();
        if (!normalized.equals("posts") && !normalized.equals("pages") && !normalized.equals("articles")) {
            throw new ValidationException("Invalid content type (must be posts, pages, or articles)", "type", type);
        }

        return normalized;
    }
}
