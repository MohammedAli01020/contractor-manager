package com.contractor.app.accounting.domain;

import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.contractor.app.accounting.infra.JournalEntryRepository;
import com.contractor.app.accounting.infra.JournalLineRepository;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.driver.infra.DriverRepository;
import com.contractor.app.equipment.infra.EquipmentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The single entry point for posting accounting journals. Callers provide a source document;
 * this service validates and persists a balanced, tenant-scoped, immutable journal atomically.
 */
@Service
public class JournalPostingService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final ChartAccountRepository accounts;
    private final JournalEntryRepository entries;
    private final JournalLineRepository lines;
    private final DriverRepository drivers;
    private final EquipmentRepository equipment;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public JournalPostingService(ChartAccountRepository accounts, JournalEntryRepository entries,
                                 JournalLineRepository lines, DriverRepository drivers,
                                 EquipmentRepository equipment, PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.entries = entries;
        this.lines = lines;
        this.drivers = drivers;
        this.equipment = equipment;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // Retained for isolated unit tests that do not configure a Spring transaction manager.
    JournalPostingService(ChartAccountRepository accounts, JournalEntryRepository entries,
                          JournalLineRepository lines, DriverRepository drivers,
                          EquipmentRepository equipment) {
        this.accounts = accounts;
        this.entries = entries;
        this.lines = lines;
        this.drivers = drivers;
        this.equipment = equipment;
        this.transactionTemplate = null;
    }

    public JournalEntry post(Long tenantId, Long actorUserId, JournalPostingRequest request) {
        if (tenantId == null || tenantId <= 0 || actorUserId == null || actorUserId <= 0) {
            throw ApiException.badRequest("المقاول والمستخدم المنفّذ مطلوبان للترحيل المالي.");
        }
        NormalizedJournal normalized = normalize(request);
        if (transactionTemplate == null) {
            return postNormalized(tenantId, actorUserId, normalized);
        }

        try {
            return transactionTemplate.execute(status -> postNormalized(tenantId, actorUserId, normalized));
        } catch (DataIntegrityViolationException ex) {
            // The failed transaction has been rolled back before this recovery read.
            Optional<JournalEntry> existing = entries.findByTenantIdAndPostingKey(tenantId, normalized.postingKey);
            if (existing.isPresent()) {
                JournalEntry prior = existing.get();
                if (sameRequest(tenantId, prior, normalized)) {
                    return prior;
                }
                throw ApiException.conflict("مفتاح الترحيل مستخدم بالفعل لحركة مالية مختلفة.");
            }
            if (normalized.reversalOfEntryId != null
                    && entries.existsByTenantIdAndReversalOfEntryId(tenantId, normalized.reversalOfEntryId)) {
                throw ApiException.conflict("تم عكس القيد الأصلي من قبل.");
            }
            throw ex;
        }
    }

    private Map<Long, ChartAccount> lockCashBankAccounts(Long tenantId, List<NormalizedLine> journalLines) {
        List<Long> accountIds = new ArrayList<>();
        for (NormalizedLine line : journalLines) {
            if (!accountIds.contains(line.accountId)) accountIds.add(line.accountId);
        }
        Collections.sort(accountIds);
        Map<Long, ChartAccount> locked = new LinkedHashMap<>();
        for (Long accountId : accountIds) {
            accounts.findCashOrBankByIdAndTenantIdForUpdate(accountId, tenantId)
                    .ifPresent(account -> locked.put(accountId, account));
        }
        return locked;
    }

    private void validateCashBankFloors(Long tenantId, List<NormalizedLine> journalLines,
                                        Map<Long, ChartAccount> lockedAccounts) {
        for (ChartAccount account : lockedAccounts.values()) {
            BigDecimal netChange = ZERO;
            for (NormalizedLine line : journalLines) {
                if (account.getId().equals(line.accountId)) {
                    netChange = netChange.add(line.debit).subtract(line.credit);
                }
            }
            BigDecimal currentBalance = lines.calculateDebitNormalBalance(tenantId, account.getId());
            if (currentBalance.add(netChange).compareTo(ZERO) < 0) {
                String name = "1000".equals(account.getAccountCode()) ? "الخزينة" : "البنك";
                throw ApiException.conflict("الرصيد المتاح في حساب " + name
                        + " غير كافٍ لإتمام العملية. لا يمكن أن يصبح الرصيد سالبًا.");
            }
        }
    }

    private JournalEntry postNormalized(Long tenantId, Long actorUserId, NormalizedJournal normalized) {
        Optional<JournalEntry> existing = entries.findByTenantIdAndPostingKey(tenantId, normalized.postingKey);
        if (existing.isPresent()) {
            JournalEntry prior = existing.get();
            if (sameRequest(tenantId, prior, normalized)) {
                return prior;
            }
            throw ApiException.conflict("مفتاح الترحيل مستخدم بالفعل لحركة مالية مختلفة.");
        }

        Map<Long, ChartAccount> lockedCashBankAccounts = lockCashBankAccounts(tenantId, normalized.lines);

        for (NormalizedLine line : normalized.lines) {
            ChartAccount account = lockedCashBankAccounts.containsKey(line.accountId)
                    ? lockedCashBankAccounts.get(line.accountId)
                    : accounts.findByIdAndTenantId(line.accountId, tenantId)
                    .orElseThrow(() -> ApiException.notFound("الحساب المحاسبي غير موجود."));
            if (!account.isActive()) {
                throw ApiException.badRequest("لا يمكن الترحيل على حساب محاسبي معطّل.");
            }
            if (line.driverId != null && !drivers.findByIdAndTenantId(line.driverId, tenantId).isPresent()) {
                throw ApiException.notFound("السائق المرتبط بالقيد غير موجود.");
            }
            if (line.equipmentId != null
                    && !equipment.findByIdAndTenantId(line.equipmentId, tenantId).isPresent()) {
                throw ApiException.notFound("المعدة المرتبطة بالقيد غير موجودة.");
            }
        }

        validateCashBankFloors(tenantId, normalized.lines, lockedCashBankAccounts);

        if (normalized.reversalOfEntryId != null) {
            JournalEntry original = entries.findByIdAndTenantId(normalized.reversalOfEntryId, tenantId)
                    .orElseThrow(() -> ApiException.notFound("القيد الأصلي المراد عكسه غير موجود."));
            if (original.getReversalOfEntryId() != null) {
                throw ApiException.badRequest("لا يمكن عكس قيد عكسي؛ أنشئ قيد تصحيح جديدًا بعد المراجعة.");
            }
            if (entries.existsByTenantIdAndReversalOfEntryId(tenantId, original.getId())) {
                throw ApiException.conflict("تم عكس القيد الأصلي من قبل.");
            }
            requireExactReversal(tenantId, original, normalized.lines);
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        JournalEntry entry = new JournalEntry(tenantId, normalized.postingKey, normalized.sourceType,
                normalized.sourceId, normalized.accountingDate, normalized.description, actorUserId,
                normalized.reversalOfEntryId, now);
        JournalEntry saved = entries.saveAndFlush(entry);

        List<JournalLine> postedLines = new ArrayList<>();
        for (int i = 0; i < normalized.lines.size(); i++) {
            NormalizedLine line = normalized.lines.get(i);
            postedLines.add(new JournalLine(tenantId, saved.getId(), i + 1, line.accountId,
                    line.description, line.debit, line.credit, line.driverId, line.equipmentId));
        }
        lines.saveAll(postedLines);
        return saved;
    }

    private NormalizedJournal normalize(JournalPostingRequest request) {
        if (request == null) {
            throw ApiException.badRequest("بيانات القيد المحاسبي مطلوبة.");
        }
        String postingKey = required(request.getPostingKey(), 120, "مفتاح الترحيل");
        String sourceType = required(request.getSourceType(), 40, "نوع المصدر").toUpperCase(Locale.ROOT);
        if (!sourceType.matches("[A-Z0-9_]+")) {
            throw ApiException.badRequest("نوع مصدر القيد غير صالح.");
        }
        String description = required(request.getDescription(), 300, "وصف القيد");
        if (request.getAccountingDate() == null) {
            throw ApiException.badRequest("تاريخ القيد المحاسبي مطلوب.");
        }
        List<JournalLineRequest> requestedLines = request.getLines();
        if (requestedLines == null || requestedLines.size() < 2) {
            throw ApiException.badRequest("القيد المحاسبي يجب أن يحتوي على سطرين على الأقل.");
        }

        List<NormalizedLine> normalizedLines = new ArrayList<>();
        BigDecimal totalDebit = ZERO;
        BigDecimal totalCredit = ZERO;
        int lineNumber = 0;
        for (JournalLineRequest line : requestedLines) {
            lineNumber++;
            if (line == null || line.getAccountId() == null || line.getAccountId() <= 0) {
                throw ApiException.badRequest("الحساب مطلوب لكل سطر في القيد.");
            }
            BigDecimal debit = amount(line.getDebit(), "المدين");
            BigDecimal credit = amount(line.getCredit(), "الدائن");
            if ((debit.signum() > 0) == (credit.signum() > 0)) {
                throw ApiException.badRequest("كل سطر يجب أن يحتوي على مبلغ مدين أو دائن فقط.");
            }
            String lineDescription = optional(line.getDescription(), 300);
            normalizedLines.add(new NormalizedLine(line.getAccountId(), lineDescription, debit, credit,
                    line.getDriverId(), line.getEquipmentId()));
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw ApiException.badRequest("القيد غير متوازن: إجمالي المدين يجب أن يساوي إجمالي الدائن.");
        }
        if (request.getSourceId() != null && request.getSourceId() <= 0) {
            throw ApiException.badRequest("معرّف مصدر القيد غير صالح.");
        }
        if (request.getReversalOfEntryId() != null && request.getReversalOfEntryId() <= 0) {
            throw ApiException.badRequest("معرّف القيد الأصلي غير صالح.");
        }
        return new NormalizedJournal(postingKey, sourceType, request.getSourceId(), request.getAccountingDate(),
                description, request.getReversalOfEntryId(), normalizedLines);
    }

    private BigDecimal amount(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw ApiException.badRequest("قيمة " + field + " يجب أن تكون صفرًا أو أكثر.");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw ApiException.badRequest("لا يمكن أن يحتوي المبلغ على أكثر من خانتين عشريتين.");
        }
    }

    private String required(String value, int maxLength, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw ApiException.badRequest(field + " مطلوب وبحد أقصى " + maxLength + " حرفًا.");
        }
        return normalized;
    }

    private String optional(String value, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw ApiException.badRequest("وصف سطر القيد أطول من المسموح.");
        }
        return normalized;
    }

    private boolean sameRequest(Long tenantId, JournalEntry prior, NormalizedJournal request) {
        if (!Objects.equals(prior.getSourceType(), request.sourceType)
                || !Objects.equals(prior.getSourceId(), request.sourceId)
                || !Objects.equals(prior.getAccountingDate(), request.accountingDate)
                || !Objects.equals(prior.getDescription(), request.description)
                || !Objects.equals(prior.getReversalOfEntryId(), request.reversalOfEntryId)) {
            return false;
        }
        List<JournalLine> priorLines = lines.findByTenantIdAndJournalEntryIdOrderByLineNumberAsc(
                tenantId, prior.getId());
        if (priorLines.size() != request.lines.size()) {
            return false;
        }
        for (int i = 0; i < priorLines.size(); i++) {
            JournalLine a = priorLines.get(i);
            NormalizedLine b = request.lines.get(i);
            if (!Objects.equals(a.getAccountId(), b.accountId)
                    || !Objects.equals(a.getDescription(), b.description)
                    || a.getDebit().compareTo(b.debit) != 0
                    || a.getCredit().compareTo(b.credit) != 0
                    || !Objects.equals(a.getDriverId(), b.driverId)
                    || !Objects.equals(a.getEquipmentId(), b.equipmentId)) {
                return false;
            }
        }
        return true;
    }

    private void requireExactReversal(Long tenantId, JournalEntry original, List<NormalizedLine> reversalLines) {
        List<JournalLine> originalLines = lines.findByTenantIdAndJournalEntryIdOrderByLineNumberAsc(
                tenantId, original.getId());
        if (originalLines.size() != reversalLines.size()) {
            throw ApiException.badRequest("القيد العكسي يجب أن يعكس جميع سطور القيد الأصلي.");
        }
        for (int i = 0; i < originalLines.size(); i++) {
            JournalLine oldLine = originalLines.get(i);
            NormalizedLine newLine = reversalLines.get(i);
            if (!Objects.equals(oldLine.getAccountId(), newLine.accountId)
                    || oldLine.getDebit().compareTo(newLine.credit) != 0
                    || oldLine.getCredit().compareTo(newLine.debit) != 0
                    || !Objects.equals(oldLine.getDriverId(), newLine.driverId)
                    || !Objects.equals(oldLine.getEquipmentId(), newLine.equipmentId)) {
                throw ApiException.badRequest("قيم القيد العكسي لا تطابق القيد الأصلي.");
            }
        }
    }

    private static final class NormalizedJournal {
        private final String postingKey;
        private final String sourceType;
        private final Long sourceId;
        private final java.time.LocalDate accountingDate;
        private final String description;
        private final Long reversalOfEntryId;
        private final List<NormalizedLine> lines;

        private NormalizedJournal(String postingKey, String sourceType, Long sourceId,
                                  java.time.LocalDate accountingDate, String description,
                                  Long reversalOfEntryId, List<NormalizedLine> lines) {
            this.postingKey = postingKey;
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.accountingDate = accountingDate;
            this.description = description;
            this.reversalOfEntryId = reversalOfEntryId;
            this.lines = lines;
        }
    }

    private static final class NormalizedLine {
        private final Long accountId;
        private final String description;
        private final BigDecimal debit;
        private final BigDecimal credit;
        private final Long driverId;
        private final Long equipmentId;

        private NormalizedLine(Long accountId, String description, BigDecimal debit, BigDecimal credit,
                               Long driverId, Long equipmentId) {
            this.accountId = accountId;
            this.description = description;
            this.debit = debit;
            this.credit = credit;
            this.driverId = driverId;
            this.equipmentId = equipmentId;
        }
    }
}
