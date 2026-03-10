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

    @Value("${aem.asset-download:true}")
    private boolean assetDownloadEnabled;

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
            byte[] data = client.get()
                    .uri(sourceUrl)
                    .retrieve()
                    .body(byte[].class);

            if (data == null || data.length == 0) {
                return AssetManifestEntry.failed(damPath, sourceUrl, "Empty response");
            }

            Path assetDir = jcrRoot.resolve(damPath.replaceFirst("^/", ""));
            Files.createDirectories(assetDir);

            writeAssetContent(assetDir, damPath, sourceUrl, data);

            String mimeType = guessMimeType(damPath, data);
            return AssetManifestEntry.success(damPath, sourceUrl, assetDir, data.length, mimeType);
        } catch (Exception e) {
            log.warn("Failed to ingest asset {} from {}", damPath, sourceUrl, e);
            return AssetManifestEntry.failed(damPath, sourceUrl, e.getMessage());
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

    private record AssetRef(String fileReference, String sourceUrl) {}

    private record AssetManifestEntry(
            String damPath,
            String sourceUrl,
            String localPath,
            int bytes,
            String mimeType,
            String status,
            String error
    ) {
        static AssetManifestEntry success(String damPath, String sourceUrl, Path localPath, int bytes, String mimeType) {
            return new AssetManifestEntry(damPath, sourceUrl,
                    localPath != null ? localPath.toString() : null,
                    bytes, mimeType, "success", null);
        }

        static AssetManifestEntry failed(String damPath, String sourceUrl, String error) {
            return new AssetManifestEntry(damPath, sourceUrl, null, 0,
                    null, "failed", Objects.toString(error, "unknown"));
        }
    }
}
