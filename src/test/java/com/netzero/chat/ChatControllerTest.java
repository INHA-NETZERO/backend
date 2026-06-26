package com.netzero.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netzero.chat.controller.ChatController;
import com.netzero.chat.dto.ChatRequest;
import com.netzero.chat.dto.ChatResponse;
import com.netzero.chat.service.ChatService;
import com.netzero.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
@Import(SecurityConfig.class)
class ChatControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    ChatService chatService;

    @Test
    void chat_returns200AndSuccessTrue() throws Exception {
        ChatResponse mockResponse = new ChatResponse(
                "우유 12L를 주문하세요.",
                List.of(1L),
                false,
                120,
                42
        );
        when(chatService.chat(any(ChatRequest.class))).thenReturn(mockResponse);

        ChatRequest request = new ChatRequest(1L, LocalDate.of(2026, 7, 1), 1L, "얼마나 주문해야 하나요?", "ko");

        mockMvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.answer").value("우유 12L를 주문하세요."))
                .andExpect(jsonPath("$.data.tokens").value(42))
                .andExpect(jsonPath("$.data.cacheHit").value(false));
    }
}
