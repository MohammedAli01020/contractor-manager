package com.contractor.app.workforce.domain;

import com.contractor.app.accounting.domain.AccountType;
import com.contractor.app.accounting.domain.ChartAccount;
import com.contractor.app.accounting.domain.DefaultChartOfAccountsService;
import com.contractor.app.accounting.domain.JournalEntry;
import com.contractor.app.accounting.domain.JournalLineRequest;
import com.contractor.app.accounting.domain.JournalPostingRequest;
import com.contractor.app.accounting.domain.JournalPostingService;
import com.contractor.app.accounting.domain.SystemAccountCode;
import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.contractor.app.accounting.infra.JournalLineRepository;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.idempotency.FinancialIdempotencyService;
import com.contractor.app.common.idempotency.RequestFingerprint;
import com.contractor.app.common.security.AuthenticatedUser;
import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.driver.api.dto.DriverLedgerAdvanceRequest;
import com.contractor.app.driver.api.dto.DriverLedgerPaymentRequest;
import com.contractor.app.workforce.api.dto.WorkforceLedgerEntryResponse;
import com.contractor.app.workforce.infra.WorkforceLedgerEntryRepository;
import com.contractor.app.workforce.infra.WorkforceMemberRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Canonical Workforce ledger writer. New commands append directly to the Workforce ledger;
 * legacy Driver endpoints remain separate compatibility routes during the staged migration.
 */
@Service
public class WorkforceLedgerCommandService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final WorkforceMemberRepository members;
    private final WorkforceLedgerEntryRepository ledger;
    private final ChartAccountRepository accounts;
    private final JournalLineRepository journalLines;
    private final FinancialIdempotencyService idempotency;
    private final JournalPostingService journals;
    private final DefaultChartOfAccountsService defaultAccounts;

    public WorkforceLedgerCommandService(WorkforceMemberRepository members,
            WorkforceLedgerEntryRepository ledger, ChartAccountRepository accounts,
            JournalLineRepository journalLines, FinancialIdempotencyService idempotency,
            JournalPostingService journals, DefaultChartOfAccountsService defaultAccounts) {
        this.members = members;
        this.ledger = ledger;
        this.accounts = accounts;
        this.journalLines = journalLines;
        this.idempotency = idempotency;
        this.journals = journals;
        this.defaultAccounts = defaultAccounts;
    }

    // Defaults are initialized before the canonical member lock. Balances are read only
    // after that lock under READ_COMMITTED, serializing payments/advances for one person.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WorkforceLedgerEntryResponse pay(Long memberId, DriverLedgerPaymentRequest request) {
        AuthenticatedUser user = CurrentUser.get();
        Long tenantId = user.getTenantId();
        if (request == null) throw ApiException.badRequest("بيانات صرف المستحقات مطلوبة.");
        String key = required(request.getClientRequestKey(), 120, "مفتاح العملية");
        String description = required(request.getDescription(), 300, "وصف العملية");
        BigDecimal amount = money(request.getAmount());
        if (request.getPaymentDate() == null) throw ApiException.badRequest("تاريخ الصرف مطلوب.");

        Optional<WorkforceLedgerEntry> existing = ledger.findByTenantIdAndClientRequestKey(tenantId, key);
        if (existing.isPresent()) {
            WorkforceLedgerEntry prior = existing.get();
            if (!prior.getWorkforceMemberId().equals(memberId) || !prior.getAmount().equals(amount)
                    || !prior.getTransactionDate().equals(request.getPaymentDate())
                    || !prior.getDescription().equals(description) || !"PAYMENT".equals(prior.getTransactionType())
                    || !journalCreditsAccount(prior.getJournalEntryId(), tenantId, request.getPaymentAccountId())) {
                throw ApiException.conflict("مفتاح العملية مستخدم لعملية أخرى.");
            }
            members.findByIdAndTenantId(memberId, tenantId)
                    .orElseThrow(() -> ApiException.notFound("العامل غير موجود."));
            return responseFor(tenantId, memberId, prior);
        }

        idempotency.reserve(tenantId, "WORKFORCE_LEDGER", key,
                RequestFingerprint.of("WORKFORCE_PAYMENT", memberId, request.getPaymentDate(), amount,
                        description, request.getPaymentAccountId()));
        defaultAccounts.initializeForTenant(tenantId);
        WorkforceMember member = members.findByIdAndTenantIdForUpdate(memberId, tenantId)
                .orElseThrow(() -> ApiException.notFound("العامل غير موجود."));
        Optional<WorkforceLedgerEntry> prior = ledger.findByTenantIdAndClientRequestKey(tenantId, key);
        if (prior.isPresent()) {
            WorkforceLedgerEntry entry = prior.get();
            if (!entry.getWorkforceMemberId().equals(memberId) || !entry.getAmount().equals(amount)
                    || !entry.getTransactionDate().equals(request.getPaymentDate())
                    || !entry.getDescription().equals(description) || !"PAYMENT".equals(entry.getTransactionType())
                    || !journalCreditsAccount(entry.getJournalEntryId(), tenantId, request.getPaymentAccountId())) {
                throw ApiException.conflict("مفتاح العملية مستخدم لعملية أخرى.");
            }
            return responseFor(tenantId, memberId, entry);
        }

        BigDecimal balance = ledger.sumBalance(tenantId, memberId);
        if (balance.compareTo(amount) < 0)
            throw ApiException.badRequest("المبلغ أكبر من مستحقات العامل المتاحة للصرف.");
        ChartAccount payable = defaultAccounts.requireSystemAccount(tenantId, SystemAccountCode.DRIVER_PAYABLE);
        ChartAccount cash = cashAccount(tenantId, request.getPaymentAccountId());
        JournalEntry journal = journals.post(tenantId, user.getUserId(), new JournalPostingRequest(
                "workforce-payment:" + key, "WORKFORCE_PAYMENT", memberId, request.getPaymentDate(),
                description, null, Arrays.asList(
                new JournalLineRequest(payable.getId(), "صرف مستحقات العامل", amount, ZERO,
                        null, member.getId(), null),
                new JournalLineRequest(cash.getId(), "صرف من الخزينة/البنك", ZERO, amount, null, null))));
        WorkforceLedgerEntry entry = ledger.saveAndFlush(new WorkforceLedgerEntry(tenantId, memberId,
                "PAYMENT", request.getPaymentDate(), amount, amount.negate(), description, key,
                null, journal.getId(), user.getUserId()));
        return new WorkforceLedgerEntryResponse(entry, balance.subtract(amount));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WorkforceLedgerEntryResponse advance(Long memberId, DriverLedgerAdvanceRequest request) {
        AuthenticatedUser user = CurrentUser.get();
        Long tenantId = user.getTenantId();
        if (request == null) throw ApiException.badRequest("بيانات السلفة مطلوبة.");
        String key = required(request.getClientRequestKey(), 120, "مفتاح العملية");
        String description = required(request.getDescription(), 300, "وصف العملية");
        BigDecimal amount = money(request.getAmount());
        if (request.getAdvanceDate() == null) throw ApiException.badRequest("تاريخ السلفة مطلوب.");
        String type = request.getAdvanceType() == null ? "" : request.getAdvanceType().trim().toUpperCase(Locale.ROOT);
        if (!Arrays.asList("AGAINST_EARNED", "RECOVERABLE").contains(type))
            throw ApiException.badRequest("نوع السلفة غير صالح.");
        String ledgerType = "AGAINST_EARNED".equals(type) ? "ADVANCE" : "RECOVERABLE_ADVANCE";

        Optional<WorkforceLedgerEntry> existing = ledger.findByTenantIdAndClientRequestKey(tenantId, key);
        if (existing.isPresent()) {
            WorkforceLedgerEntry prior = existing.get();
            if (!prior.getWorkforceMemberId().equals(memberId) || prior.getAmount().compareTo(amount) != 0
                    || !prior.getTransactionDate().equals(request.getAdvanceDate())
                    || !prior.getDescription().equals(description) || !ledgerType.equals(prior.getTransactionType())
                    || !journalCreditsAccount(prior.getJournalEntryId(), tenantId, request.getPaymentAccountId())) {
                throw ApiException.conflict("مفتاح العملية مستخدم بالفعل لحركة مالية مختلفة.");
            }
            members.findByIdAndTenantId(memberId, tenantId)
                    .orElseThrow(() -> ApiException.notFound("العامل غير موجود."));
            return responseFor(tenantId, memberId, prior);
        }

        idempotency.reserve(tenantId, "WORKFORCE_LEDGER", key,
                RequestFingerprint.of("WORKFORCE_ADVANCE", memberId, request.getAdvanceDate(), amount,
                        description, type, request.getPaymentAccountId()));
        defaultAccounts.initializeForTenant(tenantId);
        WorkforceMember member = members.findByIdAndTenantIdForUpdate(memberId, tenantId)
                .orElseThrow(() -> ApiException.notFound("العامل غير موجود."));
        Optional<WorkforceLedgerEntry> prior = ledger.findByTenantIdAndClientRequestKey(tenantId, key);
        if (prior.isPresent()) {
            WorkforceLedgerEntry entry = prior.get();
            if (!entry.getWorkforceMemberId().equals(memberId) || entry.getAmount().compareTo(amount) != 0
                    || !entry.getTransactionDate().equals(request.getAdvanceDate())
                    || !entry.getDescription().equals(description) || !ledgerType.equals(entry.getTransactionType())
                    || !journalCreditsAccount(entry.getJournalEntryId(), tenantId, request.getPaymentAccountId())) {
                throw ApiException.conflict("مفتاح العملية مستخدم بالفعل لحركة مالية مختلفة.");
            }
            return responseFor(tenantId, memberId, entry);
        }

        BigDecimal balance = ledger.sumBalance(tenantId, memberId);
        if ("AGAINST_EARNED".equals(type) && balance.compareTo(amount) < 0)
            throw ApiException.badRequest("السلفة أكبر من مستحقات العامل المتاحة.");
        ChartAccount debitAccount = "AGAINST_EARNED".equals(type)
                ? defaultAccounts.requireSystemAccount(tenantId, SystemAccountCode.DRIVER_PAYABLE)
                : defaultAccounts.requireSystemAccount(tenantId, SystemAccountCode.RECOVERABLE_WORKER_ADVANCES);
        ChartAccount cash = cashAccount(tenantId, request.getPaymentAccountId());
        JournalEntry journal = journals.post(tenantId, user.getUserId(), new JournalPostingRequest(
                "workforce-advance:" + key,
                "AGAINST_EARNED".equals(type) ? "WORKFORCE_ADVANCE_EARNED" : "WORKFORCE_ADVANCE_RECOVERABLE",
                memberId, request.getAdvanceDate(), description, null, Arrays.asList(
                new JournalLineRequest(debitAccount.getId(), "سلفة للعامل", amount, ZERO,
                        null, member.getId(), null),
                new JournalLineRequest(cash.getId(), "صرف سلفة من الخزينة/البنك", ZERO, amount, null, null))));
        BigDecimal effect = "AGAINST_EARNED".equals(type) ? amount.negate() : ZERO;
        WorkforceLedgerEntry entry = ledger.saveAndFlush(new WorkforceLedgerEntry(tenantId, memberId,
                ledgerType, request.getAdvanceDate(), amount, effect, description, key,
                null, journal.getId(), user.getUserId()));
        return new WorkforceLedgerEntryResponse(entry, balance.add(effect));
    }

    private WorkforceLedgerEntryResponse responseFor(Long tenantId, Long memberId, WorkforceLedgerEntry entry) {
        return new WorkforceLedgerEntryResponse(entry, ledger.sumBalance(tenantId, memberId));
    }

    private ChartAccount cashAccount(Long tenantId, Long accountId) {
        ChartAccount cash = accounts.findByIdAndTenantId(accountId, tenantId)
                .filter(ChartAccount::isActive)
                .orElseThrow(() -> ApiException.notFound("حساب الخزينة أو البنك غير موجود."));
        if (cash.getAccountType() != AccountType.ASSET || !cash.isCashBankAccount())
            throw ApiException.badRequest("الصرف يكون من حساب خزينة أو بنك مُعرّف لهذا الغرض فقط.");
        return cash;
    }

    private boolean journalCreditsAccount(Long journalId, Long tenantId, Long accountId) {
        return journalLines.findByTenantIdAndJournalEntryIdOrderByLineNumberAsc(tenantId, journalId).stream()
                .anyMatch(line -> line.getAccountId().equals(accountId) && line.getCredit().signum() > 0);
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() <= 0) throw ApiException.badRequest("المبلغ يجب أن يكون أكبر من صفر.");
        try { return value.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw ApiException.badRequest("المبلغ لا يقبل أكثر من خانتين عشريتين."); }
    }

    private String required(String value, int max, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max)
            throw ApiException.badRequest(label + " مطلوب وبحد أقصى " + max + " حرفًا.");
        return normalized;
    }
}
