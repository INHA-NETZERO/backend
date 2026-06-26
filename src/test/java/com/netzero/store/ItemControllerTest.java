package com.netzero.store;

import com.netzero.common.error.ApiException;
import com.netzero.common.error.ErrorCode;
import com.netzero.config.SecurityConfig;
import com.netzero.store.controller.ItemController;
import com.netzero.store.dto.ItemListResponse;
import com.netzero.store.service.ItemQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ItemController.class)
@Import(SecurityConfig.class)
class ItemControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ItemQueryService itemQueryService;

    @Test
    void listItems_returnsSuccessWithCount() throws Exception {
        when(itemQueryService.findAll(any(), any()))
                .thenReturn(new ItemListResponse(2, List.of()));

        mockMvc.perform(get("/api/v1/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.count").value(2));
    }

    @Test
    void getItem_notFound_returns400WithErrorCode() throws Exception {
        when(itemQueryService.findById(anyLong()))
                .thenThrow(new ApiException(ErrorCode.ITEM_NOT_FOUND));

        mockMvc.perform(get("/api/v1/items/999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));
    }

    @Test
    void listItems_invalidCategory_returns400WithValidationError() throws Exception {
        when(itemQueryService.findAll(any(), any()))
                .thenThrow(new ApiException(ErrorCode.VALIDATION_ERROR, "Unknown category: INVALID"));

        mockMvc.perform(get("/api/v1/items?category=INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
