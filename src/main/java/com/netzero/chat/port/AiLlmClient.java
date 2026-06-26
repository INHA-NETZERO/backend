package com.netzero.chat.port;

import com.netzero.common.error.ApiException;
import com.netzero.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * AI 서버(FastAPI)에 POST /v1/generate 를 호출하는 LlmPort 구현체.
 * 항상 활성화되며, 테스트에서는 LlmPort 자체를 Mock 하거나
 * aiGenerateRestClient 빈을 MockRestServiceServer 로 대체한다.
 */
@Service
public class AiLlmClient implements LlmPort {

    private final RestClient restClient;
    private final String generatePath;

    public AiLlmClient(
            @Qualifier("aiGenerateRestClient") RestClient restClient,
            @Value("${ai.generate-path:/v1/generate}") String generatePath) {
        this.restClient = restClient;
        this.generatePath = generatePath;
    }

    @Override
    public LlmResponse generate(LlmRequest req) {
        try {
            return restClient.post()
                    .uri(generatePath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .body(LlmResponse.class);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.LLM_UNAVAILABLE, "LLM server error");
        }
    }
}
