package com.netzero.chat.service;

import com.netzero.chat.dto.ChatRequest;
import com.netzero.chat.dto.ChatResponse;
import com.netzero.chat.port.Grounding;
import com.netzero.chat.port.LlmPort;
import com.netzero.chat.port.LlmRequest;
import com.netzero.chat.port.LlmResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {

    private final RagContextAssembler assembler;
    private final LlmPort llmPort;

    public ChatService(RagContextAssembler assembler, LlmPort llmPort) {
        this.assembler = assembler;
        this.llmPort = llmPort;
    }

    public ChatResponse chat(ChatRequest req) {
        Grounding grounding = assembler.assemble(req.storeId(), req.date(), req.itemId());
        LlmRequest llmReq = new LlmRequest(req.question(), req.locale(), grounding);
        LlmResponse llmResp = llmPort.generate(llmReq);
        return new ChatResponse(
                llmResp.answer(),
                List.of(req.itemId()),
                llmResp.cacheHit(),
                llmResp.latencyMs(),
                llmResp.tokens()
        );
    }
}
