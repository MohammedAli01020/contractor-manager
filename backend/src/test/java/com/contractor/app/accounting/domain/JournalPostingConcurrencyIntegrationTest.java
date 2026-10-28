package com.contractor.app.accounting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

import com.contractor.app.AbstractIntegrationTest;
import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.contractor.app.accounting.infra.JournalEntryRepository;
import com.contractor.app.accounting.infra.JournalLineRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;

class JournalPostingConcurrencyIntegrationTest extends AbstractIntegrationTest {
    @Autowired private JournalPostingService postingService;
    @Autowired private ChartAccountRepository accounts;
    @Autowired private JournalEntryRepository entries;
    @SpyBean private JournalLineRepository lines;

    private Long tenantId;
    private Long actorUserId;
    private Long debitAccountId;
    private Long creditAccountId;

    @BeforeEach
    void setUp() throws Exception {
        JsonNode registered = registerTenant("Accounting " + uniqueName("tenant"), uniqueName("owner"));
        tenantId = registered.path("user").path("tenantId").asLong();
        actorUserId = registered.path("user").path("id").asLong();
        debitAccountId = accounts.saveAndFlush(new ChartAccount(
                tenantId, uniqueName("D"), "Debit account", AccountType.ASSET, null, false)).getId();
        creditAccountId = accounts.saveAndFlush(new ChartAccount(
                tenantId, uniqueName("C"), "Credit account", AccountType.REVENUE, null, false)).getId();
    }

    @Test
    void failedLinePersistenceRollsBackEntryHeader() {
        String key = uniqueName("rollback");
        doThrow(new IllegalStateException("simulated persistence failure"))
                .when(lines).saveAll(anyList());

        assertThrows(RuntimeException.class, () -> postingService.post(tenantId, actorUserId, request(key)));
        assertThat(entries.findByTenantIdAndPostingKey(tenantId, key)).isEmpty();
    }

    private JournalPostingRequest request(String key) {
        return new JournalPostingRequest(key, "INTEGRATION_TEST", 1L, LocalDate.of(2026, 10, 28),
                "Rollback integration test", null, Arrays.asList(
                new JournalLineRequest(debitAccountId, "Debit", new BigDecimal("100.00"),
                        BigDecimal.ZERO.setScale(2), null, null),
                new JournalLineRequest(creditAccountId, "Credit", BigDecimal.ZERO.setScale(2),
                        new BigDecimal("100.00"), null, null)));
    }
}
