package com.contractor.app.common.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** استثناء أعمال متوقع: رسالته عربية وتُعرض للمستخدم كما هي. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static ApiException concurrentUpdate(String message) {
        return new ApiException(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", message);
    }

    public static ApiException activeShiftConflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "ACTIVE_SHIFT_CONFLICT", message);
    }

    public static ApiException activeShiftConflict(String message, Map<String, Object> details) {
        return new ApiException(HttpStatus.CONFLICT, "ACTIVE_SHIFT_CONFLICT", message, details);
    }
}
