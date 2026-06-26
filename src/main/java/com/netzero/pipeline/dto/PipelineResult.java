package com.netzero.pipeline.dto;

import java.time.LocalDate;

public record PipelineResult(
    Long storeId,
    LocalDate targetDate,
    int dueItems,
    int forecasted,
    int recommended,
    int carbonComputed,
    String modelVersion,
    long elapsedMs
) {}
