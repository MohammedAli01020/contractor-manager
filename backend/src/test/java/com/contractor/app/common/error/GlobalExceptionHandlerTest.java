package com.contractor.app.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsLockTimeoutToRetryableServiceUnavailableWithRetryAfter() {
        ResponseEntity<Object> response = handler.handlePessimisticLock(
                new PessimisticLockingFailureException("lock wait timeout"),
                new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("1");
        ApiError error = (ApiError) response.getBody();
        assertThat(error.getCode()).isEqualTo("LOCK_TIMEOUT");
        assertThat(error.getMessage()).contains("نفس مفتاح العملية");
    }

    @Test
    void mapsDriverTriggerSignalToConflictEvenWhenWrapped() {
        RuntimeException wrapped = new RuntimeException(
                "JPA update failed",
                new SQLException("ACTIVE_SHIFT_DRIVER_RUNNING"));

        ResponseEntity<Object> response = handler.handleUnexpected(
                wrapped, new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isInstanceOf(ApiError.class);
        ApiError error = (ApiError) response.getBody();
        assertThat(error.getCode()).isEqualTo("ACTIVE_SHIFT_CONFLICT");
        assertThat(error.getMessage()).contains("السائق");
    }

    @Test
    void mapsEquipmentTriggerSignalToConflictEvenWhenWrapped() {
        RuntimeException wrapped = new RuntimeException(
                "SQL operation failed",
                new SQLException("ACTIVE_SHIFT_EQUIPMENT_RUNNING"));

        ResponseEntity<Object> response = handler.handleUnexpected(
                wrapped, new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiError error = (ApiError) response.getBody();
        assertThat(error.getCode()).isEqualTo("ACTIVE_SHIFT_CONFLICT");
        assertThat(error.getMessage()).contains("المعدة");
    }

    @Test
    void mapsTriggerSignalToConflictWhenSpringClassifiesItAsDataIntegrityViolation() {
        DataIntegrityViolationException wrapped = new DataIntegrityViolationException(
                "database update failed", new SQLException("ACTIVE_SHIFT_EQUIPMENT_RUNNING"));

        ResponseEntity<Object> response = handler.handleDataIntegrity(
                wrapped, new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiError error = (ApiError) response.getBody();
        assertThat(error.getCode()).isEqualTo("ACTIVE_SHIFT_CONFLICT");
        assertThat(error.getMessage()).contains("المعدة");
    }

    @Test
    void leavesUnknownErrorsAsInternalServerError() {
        ResponseEntity<Object> response = handler.handleUnexpected(
                new RuntimeException("unrelated database failure"),
                new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiError error = (ApiError) response.getBody();
        assertThat(error.getCode()).isEqualTo("INTERNAL_ERROR");
    }
}
