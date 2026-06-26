package com.netzero.chat;

import com.netzero.chat.port.Grounding;
import com.netzero.chat.port.LlmPort;
import com.netzero.chat.port.LlmRequest;
import com.netzero.chat.port.LlmResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * AiLlmClient 통합 테스트.
 * MockRestServiceServer 를 RestClient.Builder 에 바인딩하고
 * @TestConfiguration 으로 aiGenerateRestClient 빈을 대체한다.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class AiLlmClientTest {

    @TestConfiguration
    static class MockAiGenerateRestClientConfig {
        private static final RestClient.Builder BUILDER =
                RestClient.builder().baseUrl("http://localhost:8000");
        static final MockRestServiceServer SERVER =
                MockRestServiceServer.bindTo(BUILDER).build();

        @Bean
        @Primary
        public RestClient aiGenerateRestClient() {
            return BUILDER.build();
        }
    }

    @Autowired
    private LlmPort llmPort;

    private static final String RESPONSE_FIXTURE = """
            {"answer":"test","cacheHit":false,"latencyMs":120,"tokens":42}
            """;

    @BeforeEach
    void setUp() {
        MockAiGenerateRestClientConfig.SERVER.reset();
    }

    @AfterEach
    void verifyServer() {
        MockAiGenerateRestClientConfig.SERVER.verify();
    }

    @Test
    void generate_deserializesAnswerAndTokens() {
        MockAiGenerateRestClientConfig.SERVER
                .expect(requestTo("http://localhost:8000/v1/generate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(RESPONSE_FIXTURE, MediaType.APPLICATION_JSON));

        Grounding grounding = new Grounding(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        LlmRequest req = new LlmRequest("우유 재고를 얼마나 주문해야 하나요?", "ko", grounding);

        LlmResponse response = llmPort.generate(req);

        assertThat(response.answer()).isEqualTo("test");
        assertThat(response.tokens()).isEqualTo(42);
        assertThat(response.cacheHit()).isFalse();
        assertThat(response.latencyMs()).isEqualTo(120);
    }
}
