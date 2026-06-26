package com.netzero.common.error;

public class ApiException extends RuntimeException {

    public final ErrorCode code;

    public ApiException(ErrorCode code) {
        super(code.defaultMessage);
        this.code = code;
    }

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
