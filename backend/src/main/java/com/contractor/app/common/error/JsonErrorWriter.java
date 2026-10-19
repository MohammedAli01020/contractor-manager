package com.contractor.app.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

/** لكتابة أخطاء الأمان (401/403) بنفس الشكل الموحّد، لأنها تحدث قبل وصول الطلب للـ Controllers. */
public final class JsonErrorWriter {

    private JsonErrorWriter() {
    }

    public static void write(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
                             HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getOutputStream(),
                new ApiError(status.value(), code, message, request.getRequestURI(), null));
    }
}
