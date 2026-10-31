package com.contractor.app.common.error;

import com.contractor.app.common.web.RequestTrackingFilter;

import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException ex, WebRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request, null, ex.getDetails());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "ليست لديك صلاحية لتنفيذ هذا الإجراء.", request, null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
                                                       WebRequest request) {
        return build(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "تم تعديل هذا السجل بواسطة مستخدم آخر. أعد التحميل ثم حاول مجددًا.", request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        String triggerError = findActiveShiftTriggerError(ex);
        if (triggerError != null) {
            String message = "ACTIVE_SHIFT_DRIVER_RUNNING".equals(triggerError)
                    ? "لا يمكن تغيير حالة السائق أو تعطيله أثناء وجود وردية نشطة."
                    : "لا يمكن تغيير حالة المعدة أو تعطيلها أثناء وجود وردية نشطة.";
            return build(HttpStatus.CONFLICT, "ACTIVE_SHIFT_CONFLICT", message, request, null);
        }

        String causeMessage = ex.getMostSpecificCause() == null
                ? ""
                : String.valueOf(ex.getMostSpecificCause().getMessage());
        log.warn("Data integrity violation: {}", causeMessage);

        // Keep these names in sync with V6__prevent_duplicate_active_shifts.sql.
        if (causeMessage.contains("uq_shifts_one_active_driver")
                || causeMessage.contains("uq_shifts_one_active_equipment")) {
            return build(HttpStatus.CONFLICT, "ACTIVE_SHIFT_CONFLICT",
                    "السائق أو المعدة مستخدم بالفعل في وردية نشطة.", request, null);
        }

        // Accounting idempotency/reversal keys are enforced by MySQL as the final
        // guard against concurrent requests. Return a deterministic conflict instead
        // of exposing a database exception as an internal-server error.
        if (causeMessage.contains("uq_journal_entries_posting_key")) {
            return build(HttpStatus.CONFLICT, "JOURNAL_POSTING_KEY_CONFLICT",
                    "مفتاح الترحيل مستخدم بالفعل. أعد تحميل القيد وتحقق من نتيجته قبل إعادة المحاولة.",
                    request, null);
        }
        if (causeMessage.contains("uq_journal_entries_reversal")) {
            return build(HttpStatus.CONFLICT, "JOURNAL_ALREADY_REVERSED",
                    "تم عكس القيد الأصلي بالفعل. حدّث البيانات قبل إعادة المحاولة.",
                    request, null);
        }

        // Map only known business uniqueness constraints. Unknown integrity failures may
        // indicate a schema, foreign-key, CHECK, or data problem and must not masquerade
        // as an ordinary business conflict.
        if (causeMessage.contains("uq_equipment_tenant_code")
                || causeMessage.contains("uq_sites_tenant_name")
                || causeMessage.contains("uq_equipment_types_tenant_name")) {
            return build(HttpStatus.CONFLICT, "CONFLICT",
                    "القيمة مستخدمة بالفعل داخل هذا المقاول.", request, null);
        }

        log.error("Unclassified database integrity violation", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "حدث خطأ في حفظ البيانات. راجع سجل الخادم لمعرفة التفاصيل.", request, null);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<Object> handlePessimisticLock(PessimisticLockingFailureException ex,
                                                        WebRequest request) {
        log.warn("Pessimistic lock could not be acquired; client may safely retry with the same idempotency key: {}",
                ex.getClass().getSimpleName());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "1");
        return new ResponseEntity<Object>(
                new ApiError(HttpStatus.SERVICE_UNAVAILABLE.value(), "LOCK_TIMEOUT",
                        "العملية المالية ما زالت قيد التنفيذ بواسطة طلب آخر. أعد المحاولة بعد قليل باستخدام نفس مفتاح العملية.",
                        requestPath(request), null, null, requestId(request)),
                headers, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        String triggerError = findActiveShiftTriggerError(ex);
        if (triggerError != null) {
            String message = "ACTIVE_SHIFT_DRIVER_RUNNING".equals(triggerError)
                    ? "لا يمكن تغيير حالة السائق أو تعطيله أثناء وجود وردية نشطة."
                    : "لا يمكن تغيير حالة المعدة أو تعطيلها أثناء وجود وردية نشطة.";
            return build(HttpStatus.CONFLICT, "ACTIVE_SHIFT_CONFLICT", message, request, null);
        }

        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "حدث خطأ غير متوقع في الخادم. حاول لاحقًا.", request, null);
    }

    private static String requestPath(WebRequest request) {
        if (request instanceof ServletWebRequest) {
            return ((ServletWebRequest) request).getRequest().getRequestURI();
        }
        return null;
    }

    private static String requestId(WebRequest request) {
        if (request instanceof ServletWebRequest) {
            Object value = ((ServletWebRequest) request).getRequest()
                    .getAttribute(RequestTrackingFilter.REQUEST_ID_ATTRIBUTE);
            return value instanceof String ? (String) value : null;
        }
        return null;
    }

    private static String findActiveShiftTriggerError(Throwable ex) {
        java.util.Set<Throwable> visited =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        Throwable current = ex;
        while (current != null && visited.add(current)) {
            String message = current.getMessage();
            if (message != null) {
                if (message.contains("ACTIVE_SHIFT_DRIVER_RUNNING")) {
                    return "ACTIVE_SHIFT_DRIVER_RUNNING";
                }
                if (message.contains("ACTIVE_SHIFT_EQUIPMENT_RUNNING")) {
                    return "ACTIVE_SHIFT_EQUIPMENT_RUNNING";
                }
            }
            current = current.getCause();
        }
        return null;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatus status,
                                                                  WebRequest request) {
        List<ApiError.FieldIssue> issues = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldIssue(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "البيانات المدخلة غير صحيحة.", request, issues);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatus status,
                                                                  WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "صيغة الطلب غير صحيحة.", request, null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatus(
            ResponseStatusException ex, WebRequest request) {

        HttpStatus status = ex.getStatus();
        String reason = ex.getReason();

        String message = (reason == null || reason.trim().isEmpty())
                ? messageFor(status)
                : reason;

        return build(status, status.name(), message, request, null);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatus status, WebRequest request) {
        return build(status, status.name(), messageFor(status), request, null);
    }

    private static String messageFor(HttpStatus status) {
        switch (status) {
            case NOT_FOUND:
                return "المسار المطلوب غير موجود.";
            case METHOD_NOT_ALLOWED:
                return "طريقة الطلب غير مدعومة.";
            case UNSUPPORTED_MEDIA_TYPE:
                return "نوع المحتوى غير مدعوم.";
            default:
                return "الطلب غير صالح.";
        }
    }

    private static ResponseEntity<Object> build(HttpStatus status, String code, String message,
                                                WebRequest request, List<ApiError.FieldIssue> issues) {
        return build(status, code, message, request, issues, null);
    }

    private static ResponseEntity<Object> build(HttpStatus status, String code, String message,
                                                WebRequest request, List<ApiError.FieldIssue> issues,
                                                java.util.Map<String, Object> details) {
        String path = null;
        String requestId = null;
        if (request instanceof ServletWebRequest) {
            javax.servlet.http.HttpServletRequest servletRequest =
                    ((ServletWebRequest) request).getRequest();
            path = servletRequest.getRequestURI();
            Object requestIdAttribute = servletRequest.getAttribute(RequestTrackingFilter.REQUEST_ID_ATTRIBUTE);
            requestId = requestIdAttribute instanceof String ? (String) requestIdAttribute : null;
        }
        return new ResponseEntity<Object>(
                new ApiError(status.value(), code, message, path, issues, details, requestId), status);
    }
}
