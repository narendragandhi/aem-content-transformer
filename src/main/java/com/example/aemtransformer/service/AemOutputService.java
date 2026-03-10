package com.example.aemtransformer.service;

import com.example.aemtransformer.model.AemPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service for writing AEM page structures to the file system.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AemOutputService {

    private final ObjectMapper objectMapper;

    @Value("${aem.output-path:./output}")
    private String outputPath;

    @Value("${aem.site-path:/content/mysite}")
    private String sitePath;

    /**
     * Writes an AEM page to a JSON file.
     *
     * @param page AEM page to serialize
     * @param pageName output file base name
     * @return path to the written file
     * @throws IOException if writing fails
     */
    public Path writePage(AemPage page, String pageName) throws IOException {
        Path outputDir = Paths.get(outputPath);
        Files.createDirectories(outputDir);

        String fileName = sanitizeFileName(pageName) + ".json";
        Path filePath = outputDir.resolve(fileName);

        ObjectMapper prettyMapper = objectMapper.copy()
                .enable(SerializationFeature.INDENT_OUTPUT);

        prettyMapper.writeValue(filePath.toFile(), page);
        log.info("AEM page written to: {}", filePath);

        return filePath;
    }

    /**
     * Writes an AEM page structure to a directory structure matching AEM's JCR.
     *
     * @param page AEM page to serialize
     * @param pagePath relative page path
     * @return path to the written .content.json
     * @throws IOException if writing fails
     */
    public Path writePageStructure(AemPage page, String pagePath) throws IOException {
        String fullPath = sitePath + "/" + pagePath.replaceAll("^/", "");
        Path pageDir = Paths.get(outputPath, fullPath.replace("/", File.separator));
        Files.createDirectories(pageDir);

        Path contentFile = pageDir.resolve(".content.json");

        ObjectMapper prettyMapper = objectMapper.copy()
                .enable(SerializationFeature.INDENT_OUTPUT);

        prettyMapper.writeValue(contentFile.toFile(), page);
        log.info("AEM page structure written to: {}", contentFile);

        return contentFile;
    }

    /**
     * Creates a content package structure for import into AEM.
     *
     * @param packageName package name
     * @return path to the created package root
     * @throws IOException if writing fails
     */
    public Path createPackageStructure(String packageName) throws IOException {
        Path packageRoot = Paths.get(outputPath, packageName);

        Path jcrRoot = packageRoot.resolve("jcr_root");
        Files.createDirectories(jcrRoot.resolve("content"));

        Path metaInf = packageRoot.resolve("META-INF/vault");
        Files.createDirectories(metaInf);

        writeFilterXml(metaInf);
        writePropertiesXml(metaInf, packageName);

        log.info("Package structure created at: {}", packageRoot);
        return packageRoot;
    }

    private void writeFilterXml(Path metaInfPath) throws IOException {
        String filterContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <workspaceFilter version="1.0">
                    <filter root="%s"/>
                </workspaceFilter>
                """.formatted(sitePath);

        Files.writeString(metaInfPath.resolve("filter.xml"), filterContent);
    }

    private void writePropertiesXml(Path metaInfPath, String packageName) throws IOException {
        String propsContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
                <properties>
                    <entry key="name">%s</entry>
                    <entry key="group">com.example</entry>
                    <entry key="version">1.0.0</entry>
                    <entry key="packageType">content</entry>
                </properties>
                """.formatted(packageName);

        Files.writeString(metaInfPath.resolve("properties.xml"), propsContent);
    }

    private String sanitizeFileName(String name) {
        String sanitized = name == null ? "" : name.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return sanitized.isEmpty() ? "page" : sanitized;
    }
}
