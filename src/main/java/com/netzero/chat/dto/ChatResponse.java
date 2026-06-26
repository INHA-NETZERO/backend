package com.netzero.chat.dto;

import java.util.List;

public record ChatResponse(String answer, List<Long> groundedOnIds, boolean cacheHit, int llmLatencyMs, int tokens) {}
