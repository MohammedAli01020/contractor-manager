package com.contractor.app.shift.domain;

import com.contractor.app.accounting.domain.ChartAccount;
import com.contractor.app.accounting.domain.DefaultChartOfAccountsService;
import com.contractor.app.accounting.domain.JournalEntry;
import com.contractor.app.accounting.domain.JournalLine;
import com.contractor.app.accounting.domain.JournalLineRequest;
import com.contractor.app.accounting.domain.JournalPostingRequest;
import com.contractor.app.accounting.domain.JournalPostingService;
import com.contractor.app.accounting.domain.SystemAccountCode;
import com.contractor.app.accounting.infra.JournalEntryRepository;
import com.contractor.app.accounting.infra.JournalLineRepository;
import com.contractor.app.audit.domain.AuditAction;
import com.contractor.app.audit.domain.AuditEntityType;
import com.contractor.app.audit.domain.AuditService;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.money.Money;
import com.contractor.app.common.util.Texts;
import com.contractor.app.driver.domain.DriverLedgerEntry;
import com.contractor.app.driver.infra.DriverLedgerEntryRepository;
import com.contractor.app.driver.infra.DriverRepository;
import com.contractor.app.equipment.domain.Equipment;
import com.contractor.app.equipment.domain.EquipmentStatus;
import com.contractor.app.equipment.infra.EquipmentRepository;
import com.contractor.app.shift.api.dto.ShiftCorrectionRequest;
import com.contractor.app.shift.api.dto.ShiftResponse;
import com.contractor.app.shift.infra.ShiftCorrectionRepository;
import com.contractor.app.shift.infra.ShiftRepository;
import com.contractor.app.site.domain.Site;
import com.contractor.app.site.infra.SiteRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * First-release correction for completed shifts. It deliberately keeps the driver identity
 * unchanged and refuses shifts whose earning may already have been consumed by payments/recovery.
 */
@Service
public class ShiftCorrectionService {

    private final ShiftRepository shifts;
    private final ShiftCorrectionRepository corrections;
    private final DriverRepository drivers;
    private final DriverLedgerEntryRepository ledger;
    private final JournalEntryRepository journals;
    private final JournalLineRepository journalLines;
    private final JournalPostingService posting;
    private final DefaultChartOfAccountsService defaultAccounts;
    private final SiteRepository sites;
    private final EquipmentRepository equipment;
    private final ShiftService shiftService;
    private final AuditService audit;
    private final EntityManager entityManager;
    private final ZoneId businessZone;

    public ShiftCorrectionService(ShiftRepository shifts, ShiftCorrectionRepository corrections,
            DriverRepository drivers, DriverLedgerEntryRepository ledger, JournalEntryRepository journals,
            JournalLineRepository journalLines, JournalPostingService posting,
            DefaultChartOfAccountsService defaultAccounts, SiteRepository sites,
            EquipmentRepository equipment, ShiftService shiftService, AuditService audit,
            EntityManager entityManager,
            @Value("${app.business-timezone:Africa/Cairo}") String businessTimezone) {
        this.shifts = shifts;
        this.corrections = corrections;
        this.drivers = drivers;
        this.ledger = ledger;
        this.journals = journals;
        this.journalLines = journalLines;
        this.posting = posting;
        this.defaultAccounts = defaultAccounts;
        this.sites = sites;
        this.equipment = equipment;
        this.shiftService = shiftService;
        this.audit = audit;
        this.entityManager = entityManager;
        this.businessZone = ZoneId.of(businessTimezone);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShiftResponse correct(Long tenantId, Long shiftId, Long actorId, ShiftCorrectionRequest request) {
        if (request == null || actorId == null || actorId <= 0) {
            throw ApiException.badRequest("بيانات التصحيح والمستخدم المنفّذ مطلوبة.");
        }
        String key = required(request.getIdempotencyKey(), 120, "مفتاح التصحيح");
        String reason = required(request.getReason(), 500, "سبب التصحيح");
        ShiftCorrection prior = corrections.findByTenantIdAndIdempotencyKey(tenantId, key).orElse(null);
        if (prior != null) {
            requireSameRequest(prior, shiftId, request, reason);
            if (!"APPLIED".equals(prior.getStatus())) {
                throw ApiException.conflict("طلب التصحيح موجود لكنه لم يكتمل؛ راجع سجل التصحيحات قبل إعادة المحاولة.");
            }
            return shiftService.get(tenantId, shiftId);
        }

        Shift shift = shifts.findByIdAndTenantIdForUpdate(shiftId, tenantId)
                .orElseThrow(() -> ApiException.notFound("الوردية غير موجودة."));
        // Recheck after acquiring the shift lock: a concurrent retry may have committed meanwhile.
        prior = corrections.findByTenantIdAndIdempotencyKey(tenantId, key).orElse(null);
        if (prior != null) {
            requireSameRequest(prior, shiftId, request, reason);
            if (!"APPLIED".equals(prior.getStatus())) {
                throw ApiException.conflict("طلب التصحيح موجود لكنه لم يكتمل؛ راجع سجل التصحيحات قبل إعادة المحاولة.");
            }
            return shiftService.get(tenantId, shiftId);
        }

        if (shift.getStatus() != ShiftStatus.COMPLETED || shift.getFinishedAt() == null) {
            throw ApiException.conflict("التصحيح متاح للورديات المكتملة فقط.");
        }
        shift.requireVersion(request.getExpectedVersion());
        if (request.getShiftDate() == null || request.getShiftDate().isAfter(LocalDate.now(businessZone))) {
            throw ApiException.badRequest("تاريخ الوردية مطلوب ولا يمكن أن يكون في المستقبل.");
        }
        if (request.getPrice() == null || request.getEquipmentOperatingCost() == null) {
            throw ApiException.badRequest("السعر وتكلفة تشغيل المعدة مطلوبان.");
        }
        String basis = shift.getCompensationBasis();
        BigDecimal correctedHours = null;
        BigDecimal correctedQuantity = null;
        BigDecimal newWage;
        if ("SHIFT".equals(basis)) {
            if (request.getDriverWage() == null || request.getDriverWage().signum() < 0
                    || request.getActualHours() != null || request.getActualQuantity() != null) {
                throw ApiException.badRequest("تصحيح وردية SHIFT يتطلب أجرًا مصححًا فقط دون ساعات أو كمية.");
            }
            newWage = money(request.getDriverWage());
        } else if ("HOUR".equals(basis)) {
            if (request.getDriverWage() != null || request.getActualHours() == null
                    || request.getActualQuantity() != null) {
                throw ApiException.badRequest("تصحيح وردية الساعة يتطلب actualHours فقط، ويُحسب الأجر تلقائيًا.");
            }
            correctedHours = measured(request.getActualHours(), 2, 6, "عدد الساعات");
            newWage = Money.scale(shift.getCompensationRate().multiply(correctedHours));
        } else if ("QUANTITY".equals(basis)) {
            if (request.getDriverWage() != null || request.getActualQuantity() == null
                    || request.getActualHours() != null) {
                throw ApiException.badRequest("تصحيح وردية الكمية يتطلب actualQuantity فقط، ويُحسب الأجر تلقائيًا.");
            }
            correctedQuantity = measured(request.getActualQuantity(), 3, 9, "الكمية");
            newWage = Money.scale(shift.getCompensationRate().multiply(correctedQuantity));
        } else {
            throw ApiException.conflict("أساس التعويض المسجل للوردية غير مدعوم للتصحيح.");
        }
        BigDecimal newPrice = money(request.getPrice());
        BigDecimal newOperatingCost = money(request.getEquipmentOperatingCost());
        String newNotes = Texts.blankToNull(request.getNotes());
        boolean protectedFieldsChanged =
                !Objects.equals(shift.getShiftDate(), request.getShiftDate())
                || !Objects.equals(shift.getShiftType(), request.getShiftType())
                || !Objects.equals(shift.getSiteId(), request.getSiteId())
                || !Objects.equals(shift.getEquipmentId(), request.getEquipmentId())
                || shift.getDriverWage().compareTo(newWage) != 0
                || !Objects.equals(shift.getActualHours(), correctedHours)
                || !Objects.equals(shift.getActualQuantity(), correctedQuantity)
                || shift.getPrice().compareTo(newPrice) != 0
                || shift.getEquipmentOperatingCost().compareTo(newOperatingCost) != 0;
        if (!protectedFieldsChanged) {
            throw ApiException.badRequest("لا توجد تغييرات تشغيلية أو مالية لتصحيحها. استخدم تعديل الملاحظات العادي إذا لزم الأمر.");
        }

        Site site = sites.findByIdAndTenantId(request.getSiteId(), tenantId)
                .orElseThrow(() -> ApiException.notFound("الموقع غير موجود."));
        if (!site.isActive() && !Objects.equals(site.getId(), shift.getSiteId())) {
            throw ApiException.badRequest("الموقع معطّل. اختر موقعًا فعّالًا.");
        }
        Equipment item = equipment.findByIdAndTenantId(request.getEquipmentId(), tenantId)
                .orElseThrow(() -> ApiException.notFound("المعدة غير موجودة."));
        if (!Objects.equals(item.getId(), shift.getEquipmentId())) {
            if (!item.isActive() || item.getStatus() == EquipmentStatus.MAINTENANCE
                    || item.getStatus() == EquipmentStatus.OUT_OF_SERVICE) {
                throw ApiException.badRequest("المعدة المختارة غير متاحة للتشغيل.");
            }
        }

        // Follow the same tenant -> driver lock order as shift finish and driver payments.
        defaultAccounts.initializeForTenant(tenantId);
        drivers.findByIdAndTenantIdForUpdate(shift.getDriverId(), tenantId)
                .orElseThrow(() -> ApiException.notFound("السائق المرتبط بالوردية غير موجود."));

        if (ledger.existsByTenantIdAndShiftIdAndTransactionType(
                tenantId, shiftId, "ADVANCE_RECOVERY")) {
            throw ApiException.conflict("لا يمكن تصحيح الوردية لأنها تضمنت استرداد سلفة. يلزم مسار تسوية مستقل.");
        }
        if (ledger.existsByTenantIdAndDriverIdAndTransactionTypeAndCreatedAtAfter(
                    tenantId, shift.getDriverId(), "PAYMENT", shift.getFinishedAt())
                || ledger.existsByTenantIdAndDriverIdAndTransactionTypeAndCreatedAtAfter(
                    tenantId, shift.getDriverId(), "ADVANCE", shift.getFinishedAt())
                || ledger.existsByTenantIdAndDriverIdAndTransactionTypeAndCreatedAtAfter(
                    tenantId, shift.getDriverId(), "ADVANCE_RECOVERY", shift.getFinishedAt())) {
            throw ApiException.conflict("لا يمكن التصحيح لوجود صرف مستحقات أو سلفة من المستحقات أو استرداد سلفة لاحق للسائق. يلزم إجراء تسوية مستقل.");
        }

        BigDecimal oldWage = money(shift.getDriverWage());
        if (oldWage.signum() < 0) {
            throw ApiException.conflict("لقطة أجر الوردية الحالية غير صالحة ولا يمكن تصحيحها بأمان.");
        }
        JournalEntry original = null;
        DriverLedgerEntry oldEarning = null;
        List<JournalLine> originalLines = new ArrayList<>();
        ChartAccount wageExpense = null;
        ChartAccount driverPayable = null;

        if (oldWage.signum() == 0) {
            // WF-05 zero-wage shifts intentionally have no wage journal or EARNING row.
            if (shift.getDriverWageJournalEntryId() != null) {
                throw ApiException.conflict("الوردية ذات الأجر الصفري مرتبطة بقيد أجر غير متوقع؛ يلزم فحص محاسبي.");
            }
        } else {
            if (shift.getDriverWageJournalEntryId() == null) {
                throw ApiException.conflict("لا يوجد قيد أجر أصلي مرتبط بالوردية.");
            }
            original = journals.findByIdAndTenantId(shift.getDriverWageJournalEntryId(), tenantId)
                    .orElseThrow(() -> ApiException.conflict("قيد أجر الوردية الأصلي غير موجود."));
            boolean initialWageJournal = "SHIFT_WAGE".equals(original.getSourceType())
                    && Objects.equals(original.getSourceId(), shiftId)
                    && Objects.equals(original.getPostingKey(), "shift-wage-" + shiftId);
            boolean priorCorrectionJournal = false;
            final Long originalSourceId = original.getSourceId();
            if ("SHIFT_WAGE_CORRECTION".equals(original.getSourceType())
                    && originalSourceId != null
                    && Objects.equals(original.getPostingKey(), "shift-wage-correction-" + originalSourceId)) {
                priorCorrectionJournal = corrections.findByTenantIdAndShiftIdAndReplacementWageJournalEntryId(
                        tenantId, shiftId, original.getId())
                        .map(previous -> "APPLIED".equals(previous.getStatus())
                                && Objects.equals(previous.getId(), originalSourceId))
                        .orElse(false);
            }
            if ((!initialWageJournal && !priorCorrectionJournal)
                    || !"POSTED".equals(original.getStatus().name())
                    || original.getReversalOfEntryId() != null) {
                throw ApiException.conflict("قيد أجر الوردية الحالي غير مطابق ولا يمكن تصحيحه بأمان.");
            }
            oldEarning = ledger.findByTenantIdAndJournalEntryId(tenantId, original.getId())
                    .orElseThrow(() -> ApiException.conflict("حركة استحقاق السائق الحالية غير موجودة."));
            if (!"EARNING".equals(oldEarning.getTransactionType())
                    || !Objects.equals(oldEarning.getDriverId(), shift.getDriverId())
                    || !Objects.equals(oldEarning.getShiftId(), shiftId)
                    || !Objects.equals(oldEarning.getJournalEntryId(), original.getId())
                    || oldEarning.getAmount().compareTo(oldWage) != 0
                    || oldEarning.getBalanceEffect().compareTo(oldWage) != 0) {
                throw ApiException.conflict("حركة دفتر السائق الأصلية غير متطابقة ولا يمكن تصحيحها بأمان.");
            }
            originalLines = journalLines.findByTenantIdAndJournalEntryIdOrderByLineNumberAsc(
                    tenantId, original.getId());
            wageExpense = defaultAccounts.requireSystemAccount(
                    tenantId, SystemAccountCode.DRIVER_WAGE_EXPENSE);
            driverPayable = defaultAccounts.requireSystemAccount(
                    tenantId, SystemAccountCode.DRIVER_PAYABLE);
            if (originalLines.size() != 2
                    || !Objects.equals(originalLines.get(0).getAccountId(), wageExpense.getId())
                    || originalLines.get(0).getDebit().compareTo(oldWage) != 0
                    || originalLines.get(0).getCredit().signum() != 0
                    || !Objects.equals(originalLines.get(0).getDriverId(), shift.getDriverId())
                    || (originalLines.get(0).getWorkforceMemberId() != null
                            && !Objects.equals(originalLines.get(0).getWorkforceMemberId(), shift.getWorkforceMemberId()))
                    || !Objects.equals(originalLines.get(1).getAccountId(), driverPayable.getId())
                    || originalLines.get(1).getDebit().signum() != 0
                    || originalLines.get(1).getCredit().compareTo(oldWage) != 0
                    || !Objects.equals(originalLines.get(1).getDriverId(), shift.getDriverId())
                    || (originalLines.get(1).getWorkforceMemberId() != null
                            && !Objects.equals(originalLines.get(1).getWorkforceMemberId(), shift.getWorkforceMemberId()))) {
                throw ApiException.conflict("سطور قيد أجر الوردية الأصلي لا تطابق الترحيل القياسي؛ يلزم فحص محاسبي.");
            }
        }
        if (newWage.signum() > 0 && wageExpense == null) {
            wageExpense = defaultAccounts.requireSystemAccount(tenantId, SystemAccountCode.DRIVER_WAGE_EXPENSE);
            driverPayable = defaultAccounts.requireSystemAccount(tenantId, SystemAccountCode.DRIVER_PAYABLE);
        }

        ShiftCorrection correction = new ShiftCorrection(tenantId, shiftId, key, reason, actorId,
                request.getExpectedVersion(), shift, request.getShiftDate(), request.getShiftType(),
                request.getSiteId(), shift.getDriverId(), request.getEquipmentId(), newWage, newPrice,
                newOperatingCost, newNotes, correctedHours, correctedQuantity);
        if (oldEarning != null) correction.setOldEarningLedgerEntryId(oldEarning.getId());
        corrections.saveAndFlush(correction);

        // Reverse a previously recognized wage only when the old snapshot was positive.
        if (original != null) {
            List<JournalLineRequest> reversalLines = new ArrayList<>();
            for (JournalLine line : originalLines) {
                reversalLines.add(new JournalLineRequest(line.getAccountId(), "عكس: " + line.getDescription(),
                        line.getCredit(), line.getDebit(), line.getDriverId(), line.getWorkforceMemberId(), line.getEquipmentId()));
            }
            JournalEntry reversal = posting.post(tenantId, actorId, new JournalPostingRequest(
                    "shift-wage-correction-reversal-" + correction.getId(),
                    "SHIFT_WAGE_CORRECTION_REVERSAL", correction.getId(), shift.getShiftDate(),
                    "عكس أجر الوردية رقم " + shiftId + " بسبب التصحيح رقم " + correction.getId(),
                    original.getId(), reversalLines));
            correction.setWageReversalJournalEntryId(reversal.getId());
            DriverLedgerEntry reversedEarning = ledger.saveAndFlush(new DriverLedgerEntry(
                    tenantId, shift.getDriverId(), "CORRECTION_REVERSAL", shift.getShiftDate(),
                    oldWage, oldWage.negate(),
                    "عكس استحقاق الوردية رقم " + shiftId + " (تصحيح " + correction.getId() + ")",
                    "shift-wage-correction-reversal-" + correction.getId(),
                    shiftId, reversal.getId(), actorId));
            correction.setReversedEarningLedgerEntryId(reversedEarning.getId());
        }

        // Zero is represented by no replacement journal and no zero-value ledger row.
        if (newWage.signum() > 0) {
            JournalEntry replacement = posting.post(tenantId, actorId, new JournalPostingRequest(
                    "shift-wage-correction-" + correction.getId(), "SHIFT_WAGE_CORRECTION",
                    correction.getId(), request.getShiftDate(),
                    "أجر الوردية المصحح رقم " + shiftId + " (تصحيح " + correction.getId() + ")", null,
                    java.util.Arrays.asList(
                        new JournalLineRequest(wageExpense.getId(), "مصروف أجر الوردية المصحح",
                                newWage, BigDecimal.ZERO, shift.getDriverId(), shift.getWorkforceMemberId(), null),
                        new JournalLineRequest(driverPayable.getId(), "مستحق السائق بعد التصحيح",
                                BigDecimal.ZERO, newWage, shift.getDriverId(), shift.getWorkforceMemberId(), null))));
            correction.setReplacementWageJournalEntryId(replacement.getId());
            DriverLedgerEntry replacementEarning = ledger.saveAndFlush(new DriverLedgerEntry(
                    tenantId, shift.getDriverId(), "EARNING", request.getShiftDate(), newWage, newWage,
                    "أجر الوردية المصحح رقم " + shiftId + " (تصحيح " + correction.getId() + ")",
                    "shift-wage-correction-" + correction.getId(), shiftId, replacement.getId(), actorId));
            correction.setReplacementEarningLedgerEntryId(replacementEarning.getId());
        }
        corrections.saveAndFlush(correction);

        AuditService.ChangeSet changes = new AuditService.ChangeSet()
                .track("shiftDate", shift.getShiftDate(), request.getShiftDate())
                .track("shiftType", shift.getShiftType(), request.getShiftType())
                .track("siteId", shift.getSiteId(), request.getSiteId())
                .track("equipmentId", shift.getEquipmentId(), request.getEquipmentId())
                .track("price", shift.getPrice(), newPrice)
                .track("driverWage", shift.getDriverWage(), newWage)
                .track("actualHours", shift.getActualHours(), correctedHours)
                .track("actualQuantity", shift.getActualQuantity(), correctedQuantity)
                .track("compensationCalculatedAmount", shift.getCompensationCalculatedAmount(), newWage)
                .track("equipmentOperatingCost", shift.getEquipmentOperatingCost(), newOperatingCost)
                .track("notes", shift.getNotes(), newNotes);
        shift.setShiftDate(request.getShiftDate());
        shift.setShiftType(request.getShiftType());
        shift.setSiteId(request.getSiteId());
        shift.setEquipmentId(request.getEquipmentId());
        shift.setPrice(newPrice);
        if ("SHIFT".equals(basis)) {
            shift.setDriverWage(newWage);
        } else {
            shift.applyMeasuredCorrection(correctedHours, correctedQuantity, newWage);
        }
        shift.setEquipmentOperatingCost(newOperatingCost);
        shift.setNotes(newNotes);
        shift.setDriverWageJournalEntryId(correction.getReplacementWageJournalEntryId());
        shifts.saveAndFlush(shift);

        entityManager.refresh(correction);
        if (!"APPLIED".equals(correction.getStatus())) {
            throw ApiException.conflict("لم تؤكد قاعدة البيانات اكتمال التصحيح؛ تم إلغاء العملية بالكامل.");
        }
        audit.recordChanges(tenantId, AuditEntityType.SHIFT, shiftId, changes);
        audit.record(tenantId, AuditEntityType.SHIFT, shiftId, AuditAction.CORRECTED,
                "correction", null, "id=" + correction.getId() + "; reason=" + reason);
        return shiftService.get(tenantId, shiftId);
    }

    private void requireSameRequest(ShiftCorrection prior, Long shiftId,
                                   ShiftCorrectionRequest request, String reason) {
        if (!Objects.equals(prior.getShiftId(), shiftId)
                || !Objects.equals(prior.getReason(), reason)
                || !Objects.equals(prior.getExpectedShiftVersion(), request.getExpectedVersion())
                || !Objects.equals(prior.getNewShiftDate(), request.getShiftDate())
                || !Objects.equals(prior.getNewShiftType(), request.getShiftType())
                || !Objects.equals(prior.getNewSiteId(), request.getSiteId())
                || !Objects.equals(prior.getNewDriverId(), prior.getOldDriverId())
                || !Objects.equals(prior.getNewEquipmentId(), request.getEquipmentId())
                || (("SHIFT".equals(prior.getNewCompensationBasis())
                        && (request.getDriverWage() == null
                            || prior.getNewDriverWage().compareTo(request.getDriverWage()) != 0))
                    || (!"SHIFT".equals(prior.getNewCompensationBasis())
                        && request.getDriverWage() != null))
                || !sameDecimal(prior.getNewActualHours(), request.getActualHours())
                || !sameDecimal(prior.getNewActualQuantity(), request.getActualQuantity())
                || prior.getNewPrice().compareTo(request.getPrice()) != 0
                || prior.getNewEquipmentOperatingCost().compareTo(request.getEquipmentOperatingCost()) != 0
                || !Objects.equals(prior.getNewNotes(), Texts.blankToNull(request.getNotes()))) {
            throw ApiException.conflict("مفتاح التصحيح مستخدم لطلب مختلف.");
        }
    }

    private boolean sameDecimal(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private BigDecimal measured(BigDecimal value, int fractionDigits, int integerDigits, String label) {
        if (value == null || value.signum() <= 0 || value.scale() > fractionDigits
                || value.precision() - value.scale() > integerDigits) {
            throw ApiException.badRequest(label + " يجب أن تكون أكبر من صفر وبدقة لا تتجاوز "
                    + fractionDigits + " خانات عشرية.");
        }
        try {
            return value.setScale(fractionDigits, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw ApiException.badRequest(label + " تحتوي على دقة غير مسموح بها.");
        }
    }

    private BigDecimal money(BigDecimal value) {
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw ApiException.badRequest("المبالغ لا تقبل أكثر من خانتين عشريتين.");
        }
    }

    private String required(String value, int max, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw ApiException.badRequest(label + " مطلوب وبحد أقصى " + max + " حرفًا.");
        }
        return normalized;
    }
}
