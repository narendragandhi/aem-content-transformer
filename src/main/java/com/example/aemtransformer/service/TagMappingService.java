package com.example.aemtransformer.service;

import com.example.aemtransformer.model.TagMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TagMappingService {

    private final WordPressTaxonomyService taxonomyService;

    @Value("${aem.tags.root:/content/cq:tags/mysite}")
    private String tagRoot;

    public List<TagMapping> mapTags(String sourceUrl, List<Long> tagIds) {
        List<String> names = taxonomyService.resolveTagNames(sourceUrl, tagIds);
        List<TagMapping> mappings = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String slug = toSlug(name);
            String path = tagRoot + "/" + slug;
            mappings.add(new TagMapping(slug, name, path));
        }
        return mappings;
    }

    private String toSlug(String name) {
        String cleaned = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        return cleaned.isBlank() ? "tag" : cleaned;
    }
}
