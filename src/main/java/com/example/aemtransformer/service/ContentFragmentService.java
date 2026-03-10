package com.example.aemtransformer.service;

import com.example.aemtransformer.model.WordPressContent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentFragmentService {

    private final ObjectMapper objectMapper;

    @Value("${aem.fragments.enabled:false}")
    private boolean fragmentsEnabled;

    @Value("${aem.fragments.content-model:/conf/mysite/settings/dam/cfm/models/article}")
    private String contentModel;

    @Value("${aem.fragments.dam-path:/content/dam/mysite/fragments}")
    private String fragmentsRoot;

    public boolean isEnabled() {
        return fragmentsEnabled;
    }

    public String writeContentFragment(Path packageRoot, WordPressContent content, List<String> tags) {
        if (!fragmentsEnabled || packageRoot == null || content == null) {
            return null;
        }

        String slug = content.getSlug() != null ? content.getSlug() : "content";
        String fragmentPath = fragmentsRoot + "/" + slug;
        Path jcrRoot = packageRoot.resolve("jcr_root");
        Path fragmentDir = jcrRoot.resolve(fragmentPath.replaceFirst("^/", "").replace("/", File.separator));

        try {
            Files.createDirectories(fragmentDir);
            Map<String, Object> fragment = buildContentFragment(content, tags);
            ObjectMapper prettyMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
            prettyMapper.writeValue(fragmentDir.resolve(".content.json").toFile(), fragment);
            return fragmentPath;
        } catch (Exception e) {
            log.warn("Failed to write content fragment {}", fragmentPath, e);
            return null;
        }
    }

    private Map<String, Object> buildContentFragment(WordPressContent content, List<String> tags) {
        Map<String, Object> root = new HashMap<>();
        root.put("jcr:primaryType", "dam:Asset");

        Map<String, Object> jcrContent = new HashMap<>();
        jcrContent.put("jcr:primaryType", "dam:AssetContent");
        jcrContent.put("cq:model", contentModel);
        jcrContent.put("contentFragment", true);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("jcr:primaryType", "nt:unstructured");
        metadata.put("dc:title", content.getTitleText());
        metadata.put("dc:description", content.getExcerptText());
        if (tags != null && !tags.isEmpty()) {
            metadata.put("cq:tags", tags.toArray(new String[0]));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("jcr:primaryType", "nt:unstructured");

        Map<String, Object> master = new HashMap<>();
        master.put("jcr:primaryType", "nt:unstructured");
        master.put("title", content.getTitleText());
        master.put("description", content.getExcerptText());
        master.put("body", content.getContentHtml());
        master.put("author", "wordpress");
        if (content.getPublishedDate() != null) {
            master.put("publishDate", content.getPublishedDate().toString());
        }

        data.put("master", master);
        jcrContent.put("metadata", metadata);
        jcrContent.put("data", data);
        root.put("jcr:content", jcrContent);

        return root;
    }
}
