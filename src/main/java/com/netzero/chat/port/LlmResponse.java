package com.netzero.chat.port;

public record LlmResponse(String answer, boolean cacheHit, int latencyMs, int tokens) {}
