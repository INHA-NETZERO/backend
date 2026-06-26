package com.netzero.chat.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatRequest(
    @NotNull Long storeId,
    @NotNull LocalDate date,
    @NotNull Long itemId,
    @NotBlank String question,
    String locale
) {}
