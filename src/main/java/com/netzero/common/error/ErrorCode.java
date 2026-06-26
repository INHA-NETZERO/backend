package com.netzero.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    INVALID_CSV(HttpStatus.BAD_REQUEST, "CSV 형식이 올바르지 않습니다."),
    ITEM_NOT_FOUND(HttpStatus.BAD_REQUEST, "품목을 찾을 수 없습니다."),
    STORE_NOT_FOUND(HttpStatus.BAD_REQUEST, "매장을 찾을 수 없습니다."),
    CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."),
    FORECAST_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "수요예측 서버를 사용할 수 없습니다."),
    WEATHER_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "날씨 정보를 가져오지 못했습니다."),
    LLM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "챗봇 서버를 사용할 수 없습니다."),
    PIPELINE_ALREADY_RUNNING(HttpStatus.CONFLICT, "이미 실행 중인 파이프라인이 있습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "내부 오류가 발생했습니다.");

    public final HttpStatus status;
    public final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
