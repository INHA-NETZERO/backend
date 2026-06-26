package com.netzero.ingest.dto;

import java.util.List;

public record IngestResult(int accepted, int rejected, List<RowError> errors) {
    public record RowError(int line, String code, String value) {}
}
