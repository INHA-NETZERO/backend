package com.netzero.common;

import com.netzero.common.error.ApiException;
import com.netzero.common.error.ErrorCode;
import com.netzero.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class Dummy {
        @GetMapping("/boom")
        String boom() {
            throw new ApiException(ErrorCode.CONTENT_NOT_FOUND, "파일을 찾을 수 없습니다.");
        }
    }

    @Test
    void returnsEnvelopeError() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Dummy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/boom"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CONTENT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("파일을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
