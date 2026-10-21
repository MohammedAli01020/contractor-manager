package com.contractor.app.common.error;

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

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException ex, WebRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request, null);
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
        log.warn("Pessimistic lock could not be acquired: {}", ex.getClass().getSimpleName());
        return build(HttpStatus.CONFLICT, "LOCK_TIMEOUT",
                "هناك عملية أخرى تعدّل هذه البيانات الآن. انتظر قليلًا ثم حاول مجددًا.", request, null);
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
        String path = request instanceof ServletWebRequest
                ? ((ServletWebRequest) request).getRequest().getRequestURI()
                : null;
        return new ResponseEntity<Object>(new ApiError(status.value(), code, message, path, issues), status);
    }
}
