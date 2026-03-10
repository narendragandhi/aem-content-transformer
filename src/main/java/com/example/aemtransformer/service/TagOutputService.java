package com.example.aemtransformer.service;

import com.example.aemtransformer.model.TagMapping;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class TagOutputService {

    private final ObjectMapper objectMapper;

    public void writeTags(Path packageRoot, List<TagMapping> tags) {
        if (packageRoot == null || tags == null || tags.isEmpty()) {
            return;
        }

        Path jcrRoot = packageRoot.resolve("jcr_root");
        ObjectMapper prettyMapper = objectMapper.copy()
                .enable(SerializationFeature.INDENT_OUTPUT);

        for (TagMapping tag : tags) {
            String path = tag.path().replaceAll("^/+", "");
            Path tagDir = jcrRoot.resolve(path.replace("/", File.separator));
            try {
                Files.createDirectories(tagDir);
                Map<String, Object> tagNode = new HashMap<>();
                tagNode.put("jcr:primaryType", "cq:Tag");
                tagNode.put("jcr:title", tag.title());
                prettyMapper.writeValue(tagDir.resolve(".content.json").toFile(), tagNode);
            } catch (Exception e) {
                log.warn("Failed to write tag {}", tag.path(), e);
            }
        }
    }
}
