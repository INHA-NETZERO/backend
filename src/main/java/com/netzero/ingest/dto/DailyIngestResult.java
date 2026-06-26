package com.netzero.ingest.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyIngestResult(LocalDate appliedDate, int accepted, int rejected, List<IngestResult.RowError> errors) {}
