package com.example.aemtransformer.model;

import java.time.Instant;

/**
 * Migration Quality & Parity Report Entry.
 * Includes "Trust Score" logic for Prime Time verification.
 */
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
        double trustScore,      // 0.0 to 1.0 (100% match)
        boolean isComplete,     // Quality gate: true if trustScore > 0.8
        String outputPath,
        Instant timestamp
) {}
