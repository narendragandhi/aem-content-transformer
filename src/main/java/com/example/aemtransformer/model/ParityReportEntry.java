package com.example.aemtransformer.model;

import java.time.Instant;

public record ParityReportEntry(
        String key,
        String slug,
        boolean titleMatch,
        int sourceTagCount,
        int aemTagCount,
        int sourceImageCount,
        int aemImageCount,
        int sourceTextLength,
        int aemTextLength,
        String outputPath,
        Instant timestamp
) {}
