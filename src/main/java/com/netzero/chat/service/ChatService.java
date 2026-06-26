package com.netzero.chat.service;

import com.netzero.chat.dto.ChatRequest;
import com.netzero.chat.dto.ChatResponse;
import com.netzero.chat.port.Grounding;
import com.netzero.chat.port.LlmPort;
import com.netzero.chat.port.LlmRequest;
import com.netzero.chat.port.LlmResponse;
import com.netzero.metrics.ForecastMetrics;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {

    private final RagContextAssembler assembler;
    private final LlmPort llmPort;
    private final ForecastMetrics metrics;

    public ChatService(RagContextAssembler assembler, LlmPort llmPort, ForecastMetrics metrics) {
        this.assembler = assembler;
        this.llmPort = llmPort;
        this.metrics = metrics;
    }

    public ChatResponse chat(ChatRequest req) {
        Grounding grounding = assembler.assemble(req.storeId(), req.date(), req.itemId());
        LlmRequest llmReq = new LlmRequest(req.question(), req.locale(), grounding);
        LlmResponse llmResp = llmPort.generate(llmReq);
        metrics.recordLlmCall(llmResp.tokens(), llmResp.latencyMs(), llmResp.cacheHit());
        return new ChatResponse(
                llmResp.answer(),
                List.of(req.itemId()),
                llmResp.cacheHit(),
                llmResp.latencyMs(),
                llmResp.tokens()
        );
    }
}
