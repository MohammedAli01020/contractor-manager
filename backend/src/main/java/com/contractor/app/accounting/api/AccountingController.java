package com.contractor.app.accounting.api;

import com.contractor.app.accounting.domain.ChartAccount;
import com.contractor.app.accounting.domain.DefaultChartOfAccountsService;
import com.contractor.app.accounting.domain.JournalEntry;
import com.contractor.app.accounting.domain.JournalLine;
import com.contractor.app.accounting.domain.JournalPostingRequest;
import com.contractor.app.accounting.domain.JournalPostingService;
import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.contractor.app.accounting.infra.JournalEntryRepository;
import com.contractor.app.accounting.infra.JournalLineRepository;
import com.contractor.app.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * First tenant-scoped API surface for the accounting foundation.
 * Tenant identity is always taken from the authenticated principal, never the request body.
 */
@RestController
@RequestMapping("/api/v1/accounting")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
@Tag(name = "Accounting")
public class AccountingController {

    private final ChartAccountRepository accounts;
    private final DefaultChartOfAccountsService defaultAccounts;
    private final JournalPostingService journals;
    private final JournalEntryRepository entries;
    private final JournalLineRepository lines;

    public AccountingController(ChartAccountRepository accounts, DefaultChartOfAccountsService defaultAccounts,
                                JournalPostingService journals, JournalEntryRepository entries,
                                JournalLineRepository lines) {
        this.accounts = accounts;
        this.defaultAccounts = defaultAccounts;
        this.journals = journals;
        this.entries = entries;
        this.lines = lines;
    }

    @PostMapping("/accounts/initialize-defaults")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "تهيئة دليل الحسابات الافتراضي للشركة الحالية")
    public List<AccountResponse> initializeDefaultAccounts() {
        Long tenantId = CurrentUser.tenantId();
        defaultAccounts.initializeForTenant(tenantId);
        return accounts.findByTenantIdOrderByAccountCodeAsc(tenantId).stream()
                .map(AccountResponse::new)
                .collect(Collectors.toList());
    }

    @GetMapping("/accounts")
    @Operation(summary = "دليل الحسابات للشركة الحالية")
    public List<AccountResponse> listAccounts() {
        return accounts.findByTenantIdOrderByAccountCodeAsc(CurrentUser.tenantId()).stream()
                .map(AccountResponse::new)
                .collect(Collectors.toList());
    }

    @GetMapping("/journals")
    @Operation(summary = "عرض القيود المحاسبية للشركة الحالية")
    public JournalPageResponse listJournals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw com.contractor.app.common.error.ApiException.badRequest(
                    "رقم الصفحة يجب ألا يقل عن صفر، وحجم الصفحة يجب أن يكون بين 1 و100.");
        }
        Page<JournalEntry> result = entries.findByTenantIdOrderByAccountingDateDescPostedAtDescIdDesc(
                CurrentUser.tenantId(), PageRequest.of(page, size));
        List<JournalSummaryResponse> items = result.getContent().stream()
                .map(JournalSummaryResponse::new)
                .collect(Collectors.toList());
        return new JournalPageResponse(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.hasNext(), result.hasPrevious());
    }

    @GetMapping("/journals/{journalId}")
    @Operation(summary = "عرض تفاصيل قيد محاسبي")
    public JournalResponse getJournal(@PathVariable Long journalId) {
        JournalEntry entry = entries.findByIdAndTenantId(journalId, CurrentUser.tenantId())
                .orElseThrow(() -> com.contractor.app.common.error.ApiException.notFound("القيد المحاسبي غير موجود."));
        List<JournalLine> postedLines = lines.findByTenantIdAndJournalEntryIdOrderByLineNumberAsc(
                CurrentUser.tenantId(), entry.getId());
        return new JournalResponse(entry, postedLines);
    }

    @PostMapping("/journals")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ترحيل قيد محاسبي متوازن")
    public JournalResponse postJournal(@RequestBody JournalPostingRequest request) {
        JournalEntry entry = journals.post(CurrentUser.tenantId(), CurrentUser.get().getUserId(), request);
        List<JournalLine> postedLines = lines.findByTenantIdAndJournalEntryIdOrderByLineNumberAsc(
                CurrentUser.tenantId(), entry.getId());
        return new JournalResponse(entry, postedLines);
    }

    public static final class AccountResponse {
        private final Long id;
        private final String accountCode;
        private final String name;
        private final String accountType;
        private final Long parentAccountId;
        private final boolean systemManaged;
        private final boolean active;

        AccountResponse(ChartAccount account) {
            this.id = account.getId();
            this.accountCode = account.getAccountCode();
            this.name = account.getName();
            this.accountType = account.getAccountType().name();
            this.parentAccountId = account.getParentAccountId();
            this.systemManaged = account.isSystemManaged();
            this.active = account.isActive();
        }

        public Long getId() { return id; }
        public String getAccountCode() { return accountCode; }
        public String getName() { return name; }
        public String getAccountType() { return accountType; }
        public Long getParentAccountId() { return parentAccountId; }
        public boolean isSystemManaged() { return systemManaged; }
        public boolean isActive() { return active; }
    }

    public static final class JournalPageResponse {
        private final List<JournalSummaryResponse> items;
        private final int page;
        private final int size;
        private final long totalElements;
        private final int totalPages;
        private final boolean hasNext;
        private final boolean hasPrevious;

        JournalPageResponse(List<JournalSummaryResponse> items, int page, int size,
                            long totalElements, int totalPages, boolean hasNext, boolean hasPrevious) {
            this.items = items;
            this.page = page;
            this.size = size;
            this.totalElements = totalElements;
            this.totalPages = totalPages;
            this.hasNext = hasNext;
            this.hasPrevious = hasPrevious;
        }

        public List<JournalSummaryResponse> getItems() { return items; }
        public int getPage() { return page; }
        public int getSize() { return size; }
        public long getTotalElements() { return totalElements; }
        public int getTotalPages() { return totalPages; }
        public boolean isHasNext() { return hasNext; }
        public boolean isHasPrevious() { return hasPrevious; }
    }

    public static final class JournalSummaryResponse {
        private final Long id;
        private final String postingKey;
        private final String sourceType;
        private final Long sourceId;
        private final LocalDate accountingDate;
        private final String description;
        private final String status;
        private final Long postedByUserId;
        private final LocalDateTime postedAt;

        JournalSummaryResponse(JournalEntry entry) {
            this.id = entry.getId();
            this.postingKey = entry.getPostingKey();
            this.sourceType = entry.getSourceType();
            this.sourceId = entry.getSourceId();
            this.accountingDate = entry.getAccountingDate();
            this.description = entry.getDescription();
            this.status = entry.getStatus().name();
            this.postedByUserId = entry.getPostedByUserId();
            this.postedAt = entry.getPostedAt();
        }

        public Long getId() { return id; }
        public String getPostingKey() { return postingKey; }
        public String getSourceType() { return sourceType; }
        public Long getSourceId() { return sourceId; }
        public LocalDate getAccountingDate() { return accountingDate; }
        public String getDescription() { return description; }
        public String getStatus() { return status; }
        public Long getPostedByUserId() { return postedByUserId; }
        public LocalDateTime getPostedAt() { return postedAt; }
    }

    public static final class JournalResponse {
        private final Long id;
        private final String postingKey;
        private final String sourceType;
        private final Long sourceId;
        private final LocalDate accountingDate;
        private final String description;
        private final String status;
        private final Long reversalOfEntryId;
        private final Long postedByUserId;
        private final LocalDateTime postedAt;
        private final BigDecimal totalDebit;
        private final BigDecimal totalCredit;
        private final List<JournalLineResponse> lines;

        JournalResponse(JournalEntry entry, List<JournalLine> postedLines) {
            this.id = entry.getId();
            this.postingKey = entry.getPostingKey();
            this.sourceType = entry.getSourceType();
            this.sourceId = entry.getSourceId();
            this.accountingDate = entry.getAccountingDate();
            this.description = entry.getDescription();
            this.status = entry.getStatus().name();
            this.reversalOfEntryId = entry.getReversalOfEntryId();
            this.postedByUserId = entry.getPostedByUserId();
            this.postedAt = entry.getPostedAt();
            this.lines = postedLines.stream().map(JournalLineResponse::new).collect(Collectors.toList());
            this.totalDebit = this.lines.stream().map(JournalLineResponse::getDebit)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            this.totalCredit = this.lines.stream().map(JournalLineResponse::getCredit)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public Long getId() { return id; }
        public String getPostingKey() { return postingKey; }
        public String getSourceType() { return sourceType; }
        public Long getSourceId() { return sourceId; }
        public LocalDate getAccountingDate() { return accountingDate; }
        public String getDescription() { return description; }
        public String getStatus() { return status; }
        public Long getReversalOfEntryId() { return reversalOfEntryId; }
        public Long getPostedByUserId() { return postedByUserId; }
        public LocalDateTime getPostedAt() { return postedAt; }
        public BigDecimal getTotalDebit() { return totalDebit; }
        public BigDecimal getTotalCredit() { return totalCredit; }
        public List<JournalLineResponse> getLines() { return lines; }
    }

    public static final class JournalLineResponse {
        private final Integer lineNumber;
        private final Long accountId;
        private final String description;
        private final BigDecimal debit;
        private final BigDecimal credit;
        private final Long driverId;
        private final Long equipmentId;

        JournalLineResponse(JournalLine line) {
            this.lineNumber = line.getLineNumber();
            this.accountId = line.getAccountId();
            this.description = line.getDescription();
            this.debit = line.getDebit();
            this.credit = line.getCredit();
            this.driverId = line.getDriverId();
            this.equipmentId = line.getEquipmentId();
        }

        public Integer getLineNumber() { return lineNumber; }
        public Long getAccountId() { return accountId; }
        public String getDescription() { return description; }
        public BigDecimal getDebit() { return debit; }
        public BigDecimal getCredit() { return credit; }
        public Long getDriverId() { return driverId; }
        public Long getEquipmentId() { return equipmentId; }
    }
}
