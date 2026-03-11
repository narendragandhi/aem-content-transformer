package com.example.aemtransformer.service;

import com.example.aemtransformer.service.BatchMigrationService.BatchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationReportService {

    private final ObjectMapper objectMapper;

    @Value("${migration.report.path:./output/report.json}")
    private String reportPath;

    public void writeReport(BatchResult result, Path manifestPath, Path dlqPath) {
        try {
            Path path = Path.of(reportPath);
            Files.createDirectories(path.getParent());
            Map<String, Object> report = new HashMap<>();
            report.put("timestamp", Instant.now().toString());
            report.put("totalProcessed", result.totalProcessed());
            report.put("successCount", result.successCount());
            report.put("failureCount", result.failureCount());
            report.put("skippedCount", result.skippedCount());
            report.put("manifestPath", manifestPath != null ? manifestPath.toString() : null);
            report.put("dlqPath", dlqPath != null ? dlqPath.toString() : null);

            ObjectMapper pretty = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
            pretty.writeValue(path.toFile(), report);
            log.info("Migration report written to {}", path);
        } catch (Exception e) {
            log.warn("Failed to write migration report: {}", e.getMessage());
        }
    }
}
