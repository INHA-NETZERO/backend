package com.netzero.chat.controller;

import com.netzero.chat.dto.ChatRequest;
import com.netzero.chat.dto.ChatResponse;
import com.netzero.chat.service.ChatService;
import com.netzero.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest req) {
        return ApiResponse.ok(chatService.chat(req));
    }
}
