package com.contractor.app.common.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "System")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    @Operation(summary = "فحص حالة الخادم والاتصال بقاعدة البيانات")
    public ResponseEntity<HealthResponse> health() {
        boolean databaseUp;
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            databaseUp = Integer.valueOf(1).equals(result);
        } catch (DataAccessException ex) {
            databaseUp = false;
        }
        HealthResponse body = new HealthResponse(
                databaseUp ? "UP" : "DEGRADED",
                databaseUp ? "UP" : "DOWN",
                Instant.now().truncatedTo(ChronoUnit.SECONDS));
        HttpStatus status = databaseUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(body);
    }
}
