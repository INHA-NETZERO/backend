package com.netzero.chat.port;

public interface LlmPort {
    LlmResponse generate(LlmRequest req);
}
