package com.example.aemtransformer.service;

import com.example.aemtransformer.config.AppConfig;
import com.example.aemtransformer.model.AemPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {AppConfig.class, AemOutputService.class, AssetIngestionService.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AemOutputServiceTest {

    private static final Path OUTPUT_DIR = createTempDir();

    @Autowired
    private AemOutputService aemOutputService;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("aem.output-path", () -> OUTPUT_DIR.toString());
    }

    @Test
    void writePage_withInvalidName_usesFallbackFileName() throws Exception {
        AemPage page = AemPage.create("Title", "Description");
        Path output = aemOutputService.writePage(page, "!!!");

        assertEquals("page.json", output.getFileName().toString());
        assertTrue(Files.exists(output));
    }

    @Test
    void writePagePackageWithResult_writesContentJsonInJcrRoot() throws Exception {
        AemPage page = AemPage.create("Title", "Description");
        AemOutputService.PackageWriteResult result = aemOutputService.writePagePackageWithResult(page, "test-page");

        assertNotNull(result);
        assertNotNull(result.packageRoot());
        assertNotNull(result.contentPath());
        assertTrue(Files.exists(result.contentPath()));
        assertTrue(result.contentPath().toString().contains("jcr_root"));
        assertEquals(".content.json", result.contentPath().getFileName().toString());
    }

    @Test
    void zipPackage_createsZipArchive() throws Exception {
        Path packageRoot = aemOutputService.createPackageStructure("zip-test");
        Path zipPath = aemOutputService.zipPackage(packageRoot);

        assertNotNull(zipPath);
        assertTrue(Files.exists(zipPath));
        assertEquals("zip-test.zip", zipPath.getFileName().toString());
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("aem-output-test-");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create temp output directory", e);
        }
    }
}
