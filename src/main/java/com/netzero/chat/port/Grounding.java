package com.netzero.chat.port;

import java.util.Map;

public record Grounding(
        Map<String, Object> item,
        Map<String, Object> coverage,
        Map<String, Object> forecast,
        Map<String, Object> recommendation,
        Map<String, Object> carbon,
        Map<String, Object> context
) {}
