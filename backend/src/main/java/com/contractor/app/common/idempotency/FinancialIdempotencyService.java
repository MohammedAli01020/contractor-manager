package com.contractor.app.common.idempotency;

import com.contractor.app.common.error.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserves one tenant-scoped idempotency key for one operation and request fingerprint.
 *
 * INSERT IGNORE relies on the InnoDB primary key to serialize only requests using the same
 * (tenant, scope, key). A competing insert waits for the first transaction to commit/rollback.
 * The following locking read then observes the committed fingerprint. Unrelated keys do not
 * contend with each other. Call only from the transaction that writes the financial source
 * record and journal so the reservation rolls back atomically with that work.
 */
@Service
public class FinancialIdempotencyService {
    private final JdbcTemplate jdbc;

    public FinancialIdempotencyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void reserve(Long tenantId, String scope, String requestKey, String fingerprint) {
        jdbc.update("INSERT IGNORE INTO financial_idempotency_keys "
                        + "(tenant_id, operation_scope, request_key, request_fingerprint) "
                        + "VALUES (?, ?, ?, ?)",
                tenantId, scope, requestKey, fingerprint);

        String stored = jdbc.queryForObject(
                "SELECT request_fingerprint FROM financial_idempotency_keys "
                        + "WHERE tenant_id = ? AND operation_scope = ? AND request_key = ? FOR UPDATE",
                String.class, tenantId, scope, requestKey);
        if (!fingerprint.equals(stored)) {
            throw ApiException.conflict("مفتاح العملية مستخدم بالفعل لبيانات مالية مختلفة.");
        }
    }
}
