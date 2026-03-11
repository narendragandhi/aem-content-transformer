package com.example.aemtransformer.service;

import com.example.aemtransformer.model.AemPage;
import com.example.aemtransformer.model.AemPage.ComponentNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Downloads external asset binaries and writes a minimal DAM asset structure
 * into the content package under jcr_root/content/dam.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetIngestionService {

    private static final String DAM_PREFIX = "/content/dam/";

    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private final RateLimiterService rateLimiter;

    @Value("${aem.asset.download:true}")
    private boolean assetDownloadEnabled;

    @Value("${aem.asset.api.enabled:false}")
    private boolean assetApiEnabled;

    @Value("${aem.asset.api.upload-url-template:}")
    private String assetApiUploadUrlTemplate;

    @Value("${aem.asset.api.auth.type:}")
    private String assetApiAuthType;

    @Value("${aem.asset.api.auth.user:}")
    private String assetApiAuthUser;

    @Value("${aem.asset.api.auth.password:}")
    private String assetApiAuthPassword;

    @Value("${aem.asset.api.auth.token:}")
    private String assetApiAuthToken;

    @Value("${aem.asset.api.verify.enabled:false}")
    private boolean assetVerifyEnabled;

    @Value("${aem.asset.api.verify-url-template:}")
    private String assetVerifyUrlTemplate;

    @Value("${aem.asset.api.verify.timeout-ms:60000}")
    private long assetVerifyTimeoutMs;

    @Value("${aem.asset.api.verify.interval-ms:2000}")
    private long assetVerifyIntervalMs;

    @Value("${aem.asset.retry.max-attempts:3}")
    private int assetRetryAttempts;

    @Value("${aem.asset.retry.delay-ms:1000}")
    private int assetRetryDelayMs;

    @Value("${aem.asset.max-bytes:52428800}")
    private long assetMaxBytes;

    @Value("${aem.asset.download.delay-ms:0}")
    private int assetDownloadDelayMs;

    @Value("${aem.asset.upload.delay-ms:0}")
    private int assetUploadDelayMs;

    public void ingestAssets(AemPage page, Path packageRoot) {
        if (!assetDownloadEnabled) {
            log.info("Asset download disabled; skipping DAM ingestion.");
            return;
        }
        if (page == null || page.getContent() == null || page.getContent().getRoot() == null) {
            return;
        }

        Path jcrRoot = packageRoot.resolve("jcr_root");
        List<AssetRef> refs = new ArrayList<>();
        collectAssetRefs(page.getContent().getRoot(), refs);

        if (refs.isEmpty()) {
            return;
        }

        Map<String, AssetManifestEntry> manifest = new LinkedHashMap<>();
        RestClient client = restClientBuilder.build();

        for (AssetRef ref : refs) {
            String key = ref.fileReference();
            if (manifest.containsKey(key)) {
                continue;
            }
            AssetManifestEntry entry = downloadAndWriteAsset(client, jcrRoot, ref);
            manifest.put(key, entry);
        }

        writeManifest(packageRoot, manifest);
    }

    private void collectAssetRefs(ComponentNode node, List<AssetRef> refs) {
        if (node == null) {
            return;
        }

        Map<String, Object> props = node.getProperties();
        if (props != null) {
            Object fileReference = props.get("fileReference");
            Object sourceUrl = props.get("sourceUrl");
            if (fileReference instanceof String fileRef
                    && sourceUrl instanceof String src
                    && fileRef.startsWith(DAM_PREFIX)
                    && (src.startsWith("http://") || src.startsWith("https://"))) {
                refs.add(new AssetRef(fileRef, src));
            }
        }

        if (node.getChildren() != null) {
            for (ComponentNode child : node.getChildren().values()) {
                collectAssetRefs(child, refs);
            }
        }
    }

    private AssetManifestEntry downloadAndWriteAsset(RestClient client, Path jcrRoot, AssetRef ref) {
        String damPath = ref.fileReference();
        String sourceUrl = ref.sourceUrl();

        try {
            byte[] data = downloadWithRetry(client, sourceUrl);

            if (data == null || data.length == 0) {
                return AssetManifestEntry.failed(damPath, sourceUrl, "Empty response");
            }

            if (assetMaxBytes > 0 && data.length > assetMaxBytes) {
                return AssetManifestEntry.failed(damPath, sourceUrl,
                        "Asset exceeds max size: " + data.length + " bytes");
            }

            Path assetDir = jcrRoot.resolve(damPath.replaceFirst("^/", ""));
            Files.createDirectories(assetDir);

            writeAssetContent(assetDir, damPath, sourceUrl, data);

            String mimeType = guessMimeType(damPath, data);
            String uploadStatus = maybeUploadToAem(client, damPath, data, mimeType);
            String checksum = sha256Hex(data);
            return AssetManifestEntry.success(damPath, sourceUrl, assetDir, data.length, mimeType, uploadStatus, checksum);
        } catch (Exception e) {
            log.warn("Failed to ingest asset {} from {}", damPath, sourceUrl, e);
            return AssetManifestEntry.failed(damPath, sourceUrl, e.getMessage());
        } finally {
            throttle(assetDownloadDelayMs);
        }
    }

    private void writeAssetContent(Path assetDir, String damPath, String sourceUrl, byte[] data) throws IOException {
        ObjectMapper prettyMapper = objectMapper.copy()
                .enable(SerializationFeature.INDENT_OUTPUT);

        Map<String, Object> assetNode = new HashMap<>();
        assetNode.put("jcr:primaryType", "dam:Asset");
        prettyMapper.writeValue(assetDir.resolve(".content.json").toFile(), assetNode);

        Path jcrContentDir = assetDir.resolve("jcr:content");
        Files.createDirectories(jcrContentDir);

        Map<String, Object> assetContent = new HashMap<>();
        assetContent.put("jcr:primaryType", "dam:AssetContent");
        assetContent.put("jcr:mimeType", guessMimeType(damPath, data));
        assetContent.put("jcr:lastModified", Instant.now().toString());
        prettyMapper.writeValue(jcrContentDir.resolve(".content.json").toFile(), assetContent);

        Path metadataDir = jcrContentDir.resolve("metadata");
        Files.createDirectories(metadataDir);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("jcr:primaryType", "nt:unstructured");
        metadata.put("dc:source", sourceUrl);
        metadata.put("dc:title", extractTitle(damPath));
        prettyMapper.writeValue(metadataDir.resolve(".content.json").toFile(), metadata);

        Path renditionsDir = jcrContentDir.resolve("renditions");
        Files.createDirectories(renditionsDir);
        Map<String, Object> renditions = new HashMap<>();
        renditions.put("jcr:primaryType", "nt:folder");
        prettyMapper.writeValue(renditionsDir.resolve(".content.json").toFile(), renditions);

        Path originalFile = renditionsDir.resolve("original");
        Files.write(originalFile, data);
    }

    private void writeManifest(Path packageRoot, Map<String, AssetManifestEntry> manifest) {
        if (manifest.isEmpty()) {
            return;
        }

        ObjectMapper prettyMapper = objectMapper.copy()
                .enable(SerializationFeature.INDENT_OUTPUT);
        try {
            prettyMapper.writeValue(packageRoot.resolve("asset-manifest.json").toFile(), manifest.values());
        } catch (IOException e) {
            log.warn("Failed to write asset manifest", e);
        }
    }

    private String extractTitle(String damPath) {
        if (damPath == null || damPath.isBlank()) {
            return "asset";
        }
        String[] parts = damPath.split("/");
        String last = parts.length == 0 ? damPath : parts[parts.length - 1];
        return last.isBlank() ? "asset" : last;
    }

    private String guessMimeType(String damPath, byte[] data) {
        String mime = URLConnection.guessContentTypeFromName(damPath);
        return mime != null ? mime : "application/octet-stream";
    }

    private String maybeUploadToAem(RestClient client, String damPath, byte[] data, String mimeType) {
        if (!assetApiEnabled) {
            return "skipped";
        }
        if (assetApiUploadUrlTemplate == null || assetApiUploadUrlTemplate.isBlank()) {
            log.warn("Asset API enabled but upload URL template is not configured.");
            return "skipped";
        }

        String uploadUrl = assetApiUploadUrlTemplate.replace("{damPath}", damPath);
        int attempts = 0;
        while (attempts < Math.max(1, assetRetryAttempts)) {
            attempts++;
            try {
                RestClient.RequestBodySpec request = client.post().uri(uploadUrl);
                rateLimiter.acquireAem();
                applyAuth(request);
                request.header(HttpHeaders.CONTENT_TYPE, mimeType != null ? mimeType : MediaType.APPLICATION_OCTET_STREAM_VALUE)
                        .body(data)
                        .retrieve()
                        .toBodilessEntity();
                throttle(assetUploadDelayMs);
                boolean verified = verifyAssetAvailable(client, damPath);
                return verified ? "uploaded" : "verify_failed";
            } catch (Exception e) {
                log.warn("Asset API upload failed (attempt {}/{}): {}", attempts, assetRetryAttempts, damPath);
                throttle(assetRetryDelayMs * attempts);
            }
        }
        return "failed";
    }

    private void applyAuth(RestClient.RequestBodySpec request) {
        String type = assetApiAuthType != null ? assetApiAuthType.trim().toLowerCase() : "";
        if ("basic".equals(type)) {
            if (assetApiAuthUser != null && assetApiAuthPassword != null) {
                String basic = java.util.Base64.getEncoder()
                        .encodeToString((assetApiAuthUser + ":" + assetApiAuthPassword).getBytes());
                request.header(HttpHeaders.AUTHORIZATION, "Basic " + basic);
            }
            return;
        }
        if ("bearer".equals(type)) {
            if (assetApiAuthToken != null && !assetApiAuthToken.isBlank()) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + assetApiAuthToken.trim());
            }
        }
    }

    private void applyAuth(RestClient.RequestHeadersSpec<?> request) {
        String type = assetApiAuthType != null ? assetApiAuthType.trim().toLowerCase() : "";
        if ("basic".equals(type)) {
            if (assetApiAuthUser != null && assetApiAuthPassword != null) {
                String basic = java.util.Base64.getEncoder()
                        .encodeToString((assetApiAuthUser + ":" + assetApiAuthPassword).getBytes());
                request.header(HttpHeaders.AUTHORIZATION, "Basic " + basic);
            }
            return;
        }
        if ("bearer".equals(type)) {
            if (assetApiAuthToken != null && !assetApiAuthToken.isBlank()) {
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + assetApiAuthToken.trim());
            }
        }
    }

    private boolean verifyAssetAvailable(RestClient client, String damPath) {
        if (!assetVerifyEnabled) {
            return true;
        }
        if (assetVerifyUrlTemplate == null || assetVerifyUrlTemplate.isBlank()) {
            log.warn("Asset verify enabled but verify URL template is not configured.");
            return false;
        }
        String verifyUrl = assetVerifyUrlTemplate.replace("{damPath}", damPath);
        long deadline = System.currentTimeMillis() + Math.max(0, assetVerifyTimeoutMs);
        while (System.currentTimeMillis() <= deadline) {
            try {
                RestClient.RequestHeadersSpec<?> request = client.get().uri(verifyUrl);
                applyAuth(request);
                rateLimiter.acquireAem();
                request.retrieve().toBodilessEntity();
                return true;
            } catch (Exception e) {
                throttle((int) Math.max(100, assetVerifyIntervalMs));
            }
        }
        return false;
    }

    private record AssetRef(String fileReference, String sourceUrl) {}

    private record AssetManifestEntry(
            String damPath,
            String sourceUrl,
            String localPath,
            int bytes,
            String mimeType,
            String status,
            String uploadStatus,
            String checksum,
            String error
    ) {
        static AssetManifestEntry success(String damPath, String sourceUrl, Path localPath, int bytes, String mimeType,
                                          String uploadStatus, String checksum) {
            return new AssetManifestEntry(damPath, sourceUrl,
                    localPath != null ? localPath.toString() : null,
                    bytes, mimeType, "success", uploadStatus, checksum, null);
        }

        static AssetManifestEntry failed(String damPath, String sourceUrl, String error) {
            return new AssetManifestEntry(damPath, sourceUrl, null, 0,
                    null, "failed", "failed", null, Objects.toString(error, "unknown"));
        }
    }

    private byte[] downloadWithRetry(RestClient client, String sourceUrl) {
        int attempts = 0;
        while (attempts < Math.max(1, assetRetryAttempts)) {
            attempts++;
            try {
                rateLimiter.acquireWp();
                return client.get()
                        .uri(sourceUrl)
                        .retrieve()
                        .body(byte[].class);
            } catch (Exception e) {
                log.warn("Asset download failed (attempt {}/{}): {}", attempts, assetRetryAttempts, sourceUrl);
                throttle(assetRetryDelayMs * attempts);
            }
        }
        return null;
    }

    private String sha256Hex(byte[] data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void throttle(int delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
