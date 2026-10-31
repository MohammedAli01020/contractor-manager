package com.contractor.app.accounting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.contractor.app.accounting.infra.ChartAccountRepository;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultChartOfAccountsServiceTest {

    private ChartAccountRepository accounts;
    private DefaultChartOfAccountsService service;

    @BeforeEach
    void setUp() {
        accounts = mock(ChartAccountRepository.class);
        service = new DefaultChartOfAccountsService(accounts);
    }

    @Test
    void createsTheCompleteDefaultChartForANewTenant() {
        when(accounts.findByTenantIdOrderByAccountCodeAsc(7L)).thenReturn(Collections.emptyList());

        service.initializeForTenant(7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChartAccount>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(accounts).saveAll(captor.capture());
        List<ChartAccount> created = captor.getValue();
        assertThat(created).hasSize(14);
        assertThat(created).extracting(ChartAccount::getAccountCode)
                .contains("1000", "1010", "2100", "4000", "5000", "5200");
        assertThat(created.stream().filter(ChartAccount::isCashBankAccount)
                .map(ChartAccount::getAccountCode))
                .containsExactlyInAnyOrder("1000", "1010");
        assertThat(created).allSatisfy(account -> {
            assertThat(account.getTenantId()).isEqualTo(7L);
            assertThat(account.isSystemManaged()).isTrue();
        });
    }

    @Test
    void preservesExistingAccountsAndCreatesOnlyMissingDefaultCodes() {
        ChartAccount existingCash = new ChartAccount(7L, "1000", "Custom cash name", AccountType.ASSET, null, true);
        ChartAccount customAccount = new ChartAccount(7L, "9999", "Tenant custom account", AccountType.EXPENSE, null, false);
        when(accounts.findByTenantIdOrderByAccountCodeAsc(7L))
                .thenReturn(Arrays.asList(existingCash, customAccount));

        service.initializeForTenant(7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChartAccount>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(accounts).saveAll(captor.capture());
        List<ChartAccount> created = captor.getValue();
        assertThat(created).hasSize(13);
        assertThat(created).extracting(ChartAccount::getAccountCode).doesNotContain("1000", "9999");
        assertThat(existingCash.getName()).isEqualTo("Custom cash name");
    }

    @Test
    void doesNotWriteAnythingWhenAllDefaultAccountsAlreadyExist() {
        List<ChartAccount> existing = Arrays.asList(
                new ChartAccount(7L, "1000", "Cash", AccountType.ASSET, null, true),
                new ChartAccount(7L, "1010", "Bank", AccountType.ASSET, null, true),
                new ChartAccount(7L, "1100", "Receivables", AccountType.ASSET, null, true),
                new ChartAccount(7L, "1200", "Fixed assets", AccountType.ASSET, null, true),
                new ChartAccount(7L, "1300", "Recoverable worker advances", AccountType.ASSET, null, true),
                new ChartAccount(7L, "2000", "Suppliers", AccountType.LIABILITY, null, true),
                new ChartAccount(7L, "2100", "Driver payable", AccountType.LIABILITY, null, true),
                new ChartAccount(7L, "2200", "Partner payable", AccountType.LIABILITY, null, true),
                new ChartAccount(7L, "3000", "Capital", AccountType.EQUITY, null, true),
                new ChartAccount(7L, "4000", "Shift revenue", AccountType.REVENUE, null, true),
                new ChartAccount(7L, "4100", "Other revenue", AccountType.REVENUE, null, true),
                new ChartAccount(7L, "5000", "Driver wages", AccountType.EXPENSE, null, true),
                new ChartAccount(7L, "5100", "Equipment expenses", AccountType.EXPENSE, null, true),
                new ChartAccount(7L, "5200", "Other expenses", AccountType.EXPENSE, null, true));
        when(accounts.findByTenantIdOrderByAccountCodeAsc(7L)).thenReturn(existing);

        service.initializeForTenant(7L);

        verify(accounts, never()).saveAll(anyList());
    }

    @Test
    void requiresAPersistedTenantId() {
        assertThatThrownBy(() -> service.initializeForTenant(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.initializeForTenant(0L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
