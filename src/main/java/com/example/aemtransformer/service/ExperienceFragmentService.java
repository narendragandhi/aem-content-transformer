package com.example.aemtransformer.service;

import com.example.aemtransformer.model.AemPage;
import com.example.aemtransformer.model.AemPage.ComponentNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExperienceFragmentService {

    private final ObjectMapper objectMapper;

    @Value("${aem.fragments.enabled:false}")
    private boolean fragmentsEnabled;

    @Value("${aem.fragments.xf-path:/content/experience-fragments/mysite}")
    private String xfRoot;

    @Value("${aem.fragments.xf-template:/conf/mysite/settings/wcm/templates/experience-fragment}")
    private String xfTemplate;

    @Value("${aem.fragments.embed-xf-on-page:false}")
    private boolean embedOnPage;

    public boolean isEnabled() {
        return fragmentsEnabled;
    }

    public boolean isEmbedOnPage() {
        return fragmentsEnabled && embedOnPage;
    }

    public String buildXfPath(String slug) {
        String safeSlug = slug != null && !slug.isBlank() ? slug : "fragment";
        return xfRoot + "/" + safeSlug;
    }

    public String writeExperienceFragment(Path packageRoot, String slug, String title, String fragmentPath) {
        if (!fragmentsEnabled || packageRoot == null) {
            return null;
        }

        String xfPath = buildXfPath(slug);
        Path jcrRoot = packageRoot.resolve("jcr_root");
        Path xfDir = jcrRoot.resolve(xfPath.replaceFirst("^/", "").replace("/", File.separator));

        try {
            Files.createDirectories(xfDir);
            AemPage xfPage = buildExperienceFragmentPage(title, fragmentPath);
            ObjectMapper prettyMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
            prettyMapper.writeValue(xfDir.resolve(".content.json").toFile(), xfPage);
            return xfPath;
        } catch (Exception e) {
            log.warn("Failed to write experience fragment {}", xfPath, e);
            return null;
        }
    }

    private AemPage buildExperienceFragmentPage(String title, String fragmentPath) {
        ComponentNode root = ComponentNode.builder()
                .resourceType("core/wcm/components/container/v1/container")
                .properties(new LinkedHashMap<>())
                .children(new LinkedHashMap<>())
                .build();
        root.addProperty("layout", "responsiveGrid");

        if (fragmentPath != null && !fragmentPath.isBlank()) {
            ComponentNode cfNode = ComponentNode.builder()
                    .resourceType("core/wcm/components/contentfragment/v1/contentfragment")
                    .properties(new LinkedHashMap<>())
                    .children(new LinkedHashMap<>())
                    .build();
            cfNode.addProperty("fragmentPath", fragmentPath);
            root.addChild("contentfragment_0", cfNode);
        }

        AemPage.PageContent content = AemPage.PageContent.builder()
                .title(title)
                .resourceType("core/wcm/components/experiencefragment/v1/experiencefragment")
                .template(xfTemplate)
                .root(root)
                .build();

        return AemPage.builder()
                .content(content)
                .build();
    }
}
