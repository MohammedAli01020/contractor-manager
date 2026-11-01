package com.contractor.app.accounting.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.contractor.app.accounting.domain.AccountType;
import com.contractor.app.accounting.domain.ChartAccount;
import com.contractor.app.accounting.domain.DefaultChartOfAccountsService;
import com.contractor.app.accounting.domain.JournalEntry;
import com.contractor.app.accounting.domain.JournalLine;
import com.contractor.app.accounting.domain.JournalPostingRequest;
import com.contractor.app.accounting.domain.JournalPostingService;
import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.contractor.app.accounting.infra.JournalEntryRepository;
import com.contractor.app.accounting.infra.JournalLineRepository;
import com.contractor.app.common.security.AuthenticatedUser;
import com.contractor.app.user.domain.Role;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AccountingControllerTest {

    private ChartAccountRepository accounts;
    private DefaultChartOfAccountsService defaultAccounts;
    private JournalPostingService journals;
    private JournalEntryRepository entries;
    private JournalLineRepository lines;
    private AccountingController controller;

    @BeforeEach
    void setUp() {
        accounts = org.mockito.Mockito.mock(ChartAccountRepository.class);
        defaultAccounts = org.mockito.Mockito.mock(DefaultChartOfAccountsService.class);
        journals = org.mockito.Mockito.mock(JournalPostingService.class);
        entries = org.mockito.Mockito.mock(JournalEntryRepository.class);
        lines = org.mockito.Mockito.mock(JournalLineRepository.class);
        controller = new AccountingController(accounts, defaultAccounts, journals, entries, lines);
        AuthenticatedUser principal = new AuthenticatedUser(7L, 42L, "tenant-admin", Role.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", Collections.singleton(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accountListIsAlwaysScopedToAuthenticatedTenant() {
        ChartAccount account = new ChartAccount(42L, "1000", "الخزينة", AccountType.ASSET, null, true);
        when(accounts.findByTenantIdOrderByAccountCodeAsc(42L)).thenReturn(Collections.singletonList(account));

        java.util.List<AccountingController.AccountResponse> response = controller.listAccounts();
        assertEquals(1, response.size());
        assertEquals("1000", response.get(0).getAccountCode());
        verify(accounts).findByTenantIdOrderByAccountCodeAsc(42L);
    }

    @Test
    void initializesDefaultAccountsForAuthenticatedTenantOnly() {
        ChartAccount account = new ChartAccount(42L, "1000", "الخزينة", AccountType.ASSET, null, true);
        when(accounts.findByTenantIdOrderByAccountCodeAsc(42L))
                .thenReturn(Collections.singletonList(account));

        java.util.List<AccountingController.AccountResponse> response = controller.initializeDefaultAccounts();

        verify(defaultAccounts).initializeForTenant(42L);
        verify(accounts).findByTenantIdOrderByAccountCodeAsc(42L);
        assertEquals(1, response.size());
        assertEquals("1000", response.get(0).getAccountCode());
    }

    @Test
    void journalListIsPagedAndScopedToAuthenticatedTenantWithStableOrdering() {
        JournalEntry entry = org.mockito.Mockito.mock(JournalEntry.class);
        when(entry.getId()).thenReturn(91L);
        when(entry.getPostingKey()).thenReturn("page-key");
        when(entry.getSourceType()).thenReturn("MANUAL");
        when(entry.getAccountingDate()).thenReturn(LocalDate.of(2026, 10, 31));
        when(entry.getDescription()).thenReturn("Paged journal");
        when(entry.getStatus()).thenReturn(com.contractor.app.accounting.domain.JournalEntryStatus.POSTED);
        when(entry.getPostedAt()).thenReturn(LocalDateTime.of(2026, 10, 31, 9, 0));
        when(entries.findByTenantIdOrderByAccountingDateDescPostedAtDescIdDesc(
                42L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(Collections.singletonList(entry), PageRequest.of(0, 20), 41));

        AccountingController.JournalPageResponse response = controller.listJournals(0, 20);

        verify(entries).findByTenantIdOrderByAccountingDateDescPostedAtDescIdDesc(
                42L, PageRequest.of(0, 20));
        assertEquals(1, response.getItems().size());
        assertEquals(0, response.getPage());
        assertEquals(20, response.getSize());
        assertEquals(41L, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertEquals(true, response.isHasNext());
        assertEquals(false, response.isHasPrevious());
    }

    @Test
    void rejectsInvalidJournalPaginationParameters() {
        assertThrows(com.contractor.app.common.error.ApiException.class,
                () -> controller.listJournals(-1, 20));
        assertThrows(com.contractor.app.common.error.ApiException.class,
                () -> controller.listJournals(0, 101));
        assertThrows(com.contractor.app.common.error.ApiException.class,
                () -> controller.listJournals(0, 0));
        org.mockito.Mockito.verifyNoInteractions(entries);
    }

    @Test
    void journalPostingUsesAuthenticatedTenantAndActorAndReturnsBalancedTotals() {
        JournalPostingRequest request = new JournalPostingRequest("posting-1", "MANUAL", null,
                LocalDate.of(2026, 10, 29), "Test journal", null, Collections.emptyList());
        JournalEntry entry = org.mockito.Mockito.mock(JournalEntry.class);
        when(entry.getId()).thenReturn(90L);
        when(entry.getPostingKey()).thenReturn("posting-1");
        when(entry.getSourceType()).thenReturn("MANUAL");
        when(entry.getAccountingDate()).thenReturn(LocalDate.of(2026, 10, 29));
        when(entry.getDescription()).thenReturn("Test journal");
        when(entry.getStatus()).thenReturn(com.contractor.app.accounting.domain.JournalEntryStatus.POSTED);
        when(entry.getPostedByUserId()).thenReturn(7L);
        when(entry.getPostedAt()).thenReturn(LocalDateTime.of(2026, 10, 29, 8, 0).atOffset(ZoneOffset.UTC).toLocalDateTime());

        JournalLine debit = new JournalLine(42L, 90L, 1, 1L, null,
                new BigDecimal("100.00"), BigDecimal.ZERO, null, null);
        JournalLine credit = new JournalLine(42L, 90L, 2, 2L, null,
                BigDecimal.ZERO, new BigDecimal("100.00"), null, null);
        when(journals.post(42L, 7L, request)).thenReturn(entry);
        when(lines.findByTenantIdAndJournalEntryIdOrderByLineNumberAsc(42L, 90L))
                .thenReturn(Arrays.asList(debit, credit));

        AccountingController.JournalResponse response = controller.postJournal(request);

        verify(journals).post(42L, 7L, request);
        verify(lines).findByTenantIdAndJournalEntryIdOrderByLineNumberAsc(42L, 90L);
        assertEquals(new BigDecimal("100.00"), response.getTotalDebit());
        assertEquals(new BigDecimal("100.00"), response.getTotalCredit());
        assertEquals(2, response.getLines().size());
    }
}
