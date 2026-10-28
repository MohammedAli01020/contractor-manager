package com.contractor.app.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** شكل موحّد لكل أخطاء الـ API. حقل message جاهز للعرض للمستخدم بالعربية. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private final LocalDateTime timestamp = LocalDateTime.now(ZoneOffset.UTC);
    private final int status;
    private final String code;
    private final String message;
    private final String path;
    private final List<FieldIssue> fieldErrors;
    private final Map<String, Object> details;
    private final String requestId;

    public ApiError(int status, String code, String message, String path, List<FieldIssue> fieldErrors) {
        this(status, code, message, path, fieldErrors, null, null);
    }

    public ApiError(int status, String code, String message, String path,
                    List<FieldIssue> fieldErrors, Map<String, Object> details) {
        this(status, code, message, path, fieldErrors, details, null);
    }

    public ApiError(int status, String code, String message, String path,
                    List<FieldIssue> fieldErrors, Map<String, Object> details, String requestId) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.path = path;
        this.fieldErrors = fieldErrors;
        this.details = details;
        this.requestId = requestId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public List<FieldIssue> getFieldErrors() {
        return fieldErrors;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public String getRequestId() {
        return requestId;
    }

    public static class FieldIssue {
        private final String field;
        private final String message;

        public FieldIssue(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }
    }
}
