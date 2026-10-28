package com.contractor.app.accounting.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.contractor.app.accounting.infra.JournalEntryRepository;
import com.contractor.app.accounting.infra.JournalLineRepository;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.driver.infra.DriverRepository;
import com.contractor.app.equipment.infra.EquipmentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JournalPostingServiceTest {

    private JournalPostingService service;
    private JournalEntryRepository entries;

    @BeforeEach
    void setUp() {
        entries = mock(JournalEntryRepository.class);
        service = new JournalPostingService(
                mock(ChartAccountRepository.class),
                entries,
                mock(JournalLineRepository.class),
                mock(DriverRepository.class),
                mock(EquipmentRepository.class));
    }

    @Test
    void rejectsUnbalancedJournalBeforePersistingAnything() {
        JournalPostingRequest request = request(
                line(10L, "100.00", "0.00"),
                line(20L, "0.00", "99.99"));

        assertThrows(ApiException.class, () -> service.post(1L, 7L, request));
    }

    @Test
    void rejectsLineWithBothDebitAndCredit() {
        JournalPostingRequest request = request(
                line(10L, "100.00", "5.00"),
                line(20L, "0.00", "95.00"));

        assertThrows(ApiException.class, () -> service.post(1L, 7L, request));
    }

    @Test
    void rejectsAmountsWithMoreThanTwoMeaningfulDecimalPlaces() {
        JournalPostingRequest request = request(
                line(10L, "100.001", "0.00"),
                line(20L, "0.00", "100.001"));

        assertThrows(ApiException.class, () -> service.post(1L, 7L, request));
    }


    @Test
    void rejectsReusingPostingKeyForDifferentPayload() {
        LocalDate accountingDate = LocalDate.of(2026, 10, 28);
        JournalEntry existing = mock(JournalEntry.class);
        when(existing.getSourceType()).thenReturn("TEST");
        when(existing.getSourceId()).thenReturn(1L);
        when(existing.getAccountingDate()).thenReturn(accountingDate);
        when(existing.getDescription()).thenReturn("A different journal");
        when(existing.getReversalOfEntryId()).thenReturn(null);
        when(entries.findByTenantIdAndPostingKey(1L, "test-posting-key"))
                .thenReturn(Optional.of(existing));

        JournalPostingRequest retry = request(
                line(10L, "100.00", "0.00"),
                line(20L, "0.00", "100.00"));

        assertThrows(ApiException.class, () -> service.post(1L, 7L, retry));
        verify(entries, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(JournalEntry.class));
    }

    private JournalPostingRequest request(JournalLineRequest first, JournalLineRequest second) {
        return new JournalPostingRequest("test-posting-key", "TEST", 1L, LocalDate.of(2026, 10, 28),
                "Automated journal validation test", null, Arrays.asList(first, second));
    }

    private JournalLineRequest line(Long accountId, String debit, String credit) {
        return new JournalLineRequest(accountId, null, new BigDecimal(debit), new BigDecimal(credit), null, null);
    }
}
