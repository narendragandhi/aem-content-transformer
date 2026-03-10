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
import java.util.List;

/**
 * Service for writing AEM page structures to the file system.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AemOutputService {

    private final ObjectMapper objectMapper;
    private final AssetIngestionService assetIngestionService;

    @Value("${aem.output-path:./output}")
    private String outputPath;

    @Value("${aem.site-path:/content/mysite}")
    private String sitePath;

    @Value("${aem.package-name:content-migration}")
    private String packageName;

    @Value("${aem.package-zip:false}")
    private boolean packageZipEnabled;

    @Value("${aem.filevault.enabled:false}")
    private boolean fileVaultEnabled;

    @Value("${aem.filevault.command:vault}")
    private String fileVaultCommand;

    @Value("${aem.filevault.validate:false}")
    private boolean fileVaultValidate;

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
        return writePageStructure(page, pagePath, Paths.get(outputPath));
    }

    /**
     * Writes an AEM page structure into a content package format.
     *
     * @param page AEM page to serialize
     * @param pagePath relative page path
     * @return path to the written .content.json
     * @throws IOException if writing fails
     */
    public Path writePagePackage(AemPage page, String pagePath) throws IOException {
        return writePagePackageWithResult(page, pagePath).contentPath();
    }

    /**
     * Writes an AEM page into a content package and returns details.
     *
     * @param page AEM page to serialize
     * @param pagePath relative page path
     * @return package write result
     * @throws IOException if writing fails
     */
    public PackageWriteResult writePagePackageWithResult(AemPage page, String pagePath) throws IOException {
        Path packageRoot = createPackageStructure(packageName);
        Path jcrRoot = packageRoot.resolve("jcr_root");
        Path contentPath = writePageStructure(page, pagePath, jcrRoot);
        assetIngestionService.ingestAssets(page, packageRoot);
        Path fileVaultZip = maybeBuildWithFileVault(packageRoot);
        return new PackageWriteResult(packageRoot, contentPath, fileVaultZip);
    }

    /**
     * Creates a zip file of the generated content package for direct upload.
     *
     * @param packageRoot package root directory
     * @return path to the zip file
     * @throws IOException if zipping fails
     */
    public Path zipPackage(Path packageRoot) throws IOException {
        Path zipPath = Paths.get(outputPath, packageRoot.getFileName().toString() + ".zip");
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zipPath))) {
            Files.walk(packageRoot)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        String entryName = packageRoot.relativize(path).toString().replace(File.separatorChar, '/');
                        java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(entryName);
                        try {
                            zos.putNextEntry(entry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to zip package file: " + path, e);
                        }
                    });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }

        log.info("AEM package zip written to: {}", zipPath);
        return zipPath;
    }

    public boolean isPackageZipEnabled() {
        return packageZipEnabled;
    }

    public boolean isFileVaultEnabled() {
        return fileVaultEnabled;
    }

    private Path maybeBuildWithFileVault(Path packageRoot) {
        if (!fileVaultEnabled) {
            return null;
        }

        if (fileVaultCommand == null || fileVaultCommand.isBlank()) {
            log.warn("FileVault enabled but command is not configured.");
            return null;
        }

        try {
            runFileVaultCommand(List.of("package", "-b", packageRoot.toString()));
            if (fileVaultValidate) {
                runFileVaultCommand(List.of("package", "-v", packageRoot.toString()));
            }
            Path zipPath = Paths.get(outputPath, packageRoot.getFileName().toString() + ".zip");
            if (Files.exists(zipPath)) {
                log.info("FileVault package built at: {}", zipPath);
                return zipPath;
            }
        } catch (Exception e) {
            log.warn("FileVault package build failed; falling back to internal packaging.", e);
        }

        return null;
    }

    private void runFileVaultCommand(List<String> args) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>();
        command.add(fileVaultCommand);
        command.addAll(args);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(new File(outputPath));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output;
        try (java.io.InputStream input = process.getInputStream()) {
            output = new String(input.readAllBytes());
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("FileVault command failed: " + String.join(" ", command) + "\n" + output);
        }
        log.info("FileVault output: {}", output.trim());
    }

    public record PackageWriteResult(Path packageRoot, Path contentPath, Path fileVaultZip) {}

    private Path writePageStructure(AemPage page, String pagePath, Path rootPath) throws IOException {
        String fullPath = sitePath + "/" + pagePath.replaceAll("^/", "");
        fullPath = fullPath.replaceAll("^/+", "");
        Path pageDir = rootPath.resolve(fullPath.replace("/", File.separator));
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
