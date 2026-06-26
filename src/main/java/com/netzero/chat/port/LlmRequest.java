package com.netzero.chat.port;

public record LlmRequest(String question, String locale, Grounding grounding) {}
