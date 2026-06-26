package com.netzero.chat.dto;

import java.time.LocalDate;

public record ChatRequest(Long storeId, LocalDate date, Long itemId, String question, String locale) {}
