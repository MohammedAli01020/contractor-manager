package com.contractor.app.shift.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

/**
 * Immutable before/after snapshot authorizing one audited correction of a completed shift.
 * The database trigger changes APPLYING to APPLIED in the same transaction as the shift update.
 */
@Entity
@Table(name = "shift_corrections")
public class ShiftCorrection extends BaseTenantEntity {

    @Column(name = "shift_id", nullable = false, updatable = false)
    private Long shiftId;

    @Column(name = "idempotency_key", nullable = false, length = 120, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, length = 500, updatable = false)
    private String reason;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;

    @Column(name = "expected_shift_version", nullable = false, updatable = false)
    private Long expectedShiftVersion;

    @Column(name = "old_shift_date", nullable = false, updatable = false)
    private LocalDate oldShiftDate;
    @Column(name = "new_shift_date", nullable = false, updatable = false)
    private LocalDate newShiftDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "old_shift_type", nullable = false, length = 20, updatable = false)
    private ShiftType oldShiftType;
    @Enumerated(EnumType.STRING)
    @Column(name = "new_shift_type", nullable = false, length = 20, updatable = false)
    private ShiftType newShiftType;
    @Column(name = "old_site_id", nullable = false, updatable = false)
    private Long oldSiteId;
    @Column(name = "new_site_id", nullable = false, updatable = false)
    private Long newSiteId;
    @Column(name = "old_driver_id", nullable = false, updatable = false)
    private Long oldDriverId;
    @Column(name = "new_driver_id", nullable = false, updatable = false)
    private Long newDriverId;
    @Column(name = "old_equipment_id", nullable = false, updatable = false)
    private Long oldEquipmentId;
    @Column(name = "new_equipment_id", nullable = false, updatable = false)
    private Long newEquipmentId;
    @Column(name = "old_driver_wage", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal oldDriverWage;
    @Column(name = "new_driver_wage", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal newDriverWage;
    @Column(name = "old_price", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal oldPrice;
    @Column(name = "new_price", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal newPrice;
    @Column(name = "old_equipment_operating_cost", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal oldEquipmentOperatingCost;
    @Column(name = "new_equipment_operating_cost", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal newEquipmentOperatingCost;

    // Frozen compensation inputs and measured values captured with the correction audit row.
    @Column(name = "old_compensation_basis", length = 20, updatable = false)
    private String oldCompensationBasis;
    @Column(name = "new_compensation_basis", length = 20, updatable = false)
    private String newCompensationBasis;
    @Column(name = "old_compensation_rate", precision = 15, scale = 2, updatable = false)
    private BigDecimal oldCompensationRate;
    @Column(name = "new_compensation_rate", precision = 15, scale = 2, updatable = false)
    private BigDecimal newCompensationRate;
    @Column(name = "old_compensation_unit_code", length = 30, updatable = false)
    private String oldCompensationUnitCode;
    @Column(name = "new_compensation_unit_code", length = 30, updatable = false)
    private String newCompensationUnitCode;
    @Column(name = "old_compensation_currency_code", length = 3, updatable = false)
    private String oldCompensationCurrencyCode;
    @Column(name = "new_compensation_currency_code", length = 3, updatable = false)
    private String newCompensationCurrencyCode;
    @Column(name = "old_compensation_calculated_amount", precision = 15, scale = 2, updatable = false)
    private BigDecimal oldCompensationCalculatedAmount;
    @Column(name = "new_compensation_calculated_amount", precision = 15, scale = 2, updatable = false)
    private BigDecimal newCompensationCalculatedAmount;
    @Column(name = "old_actual_hours", precision = 8, scale = 2, updatable = false)
    private BigDecimal oldActualHours;
    @Column(name = "new_actual_hours", precision = 8, scale = 2, updatable = false)
    private BigDecimal newActualHours;
    @Column(name = "old_actual_quantity", precision = 12, scale = 3, updatable = false)
    private BigDecimal oldActualQuantity;
    @Column(name = "new_actual_quantity", precision = 12, scale = 3, updatable = false)
    private BigDecimal newActualQuantity;

    @Column(name = "old_notes", length = 500, updatable = false)
    private String oldNotes;
    @Column(name = "new_notes", length = 500, updatable = false)
    private String newNotes;

    @Column(name = "old_wage_journal_entry_id", updatable = false)
    private Long oldWageJournalEntryId;
    @Column(name = "wage_reversal_journal_entry_id")
    private Long wageReversalJournalEntryId;
    @Column(name = "replacement_wage_journal_entry_id")
    private Long replacementWageJournalEntryId;
    @Column(name = "old_earning_ledger_entry_id", updatable = false)
    private Long oldEarningLedgerEntryId;
    @Column(name = "reversed_earning_ledger_entry_id")
    private Long reversedEarningLedgerEntryId;
    @Column(name = "replacement_earning_ledger_entry_id")
    private Long replacementEarningLedgerEntryId;

    // Canonical Workforce ledger references; legacy IDs above remain historical compatibility references.
    @Column(name = "old_earning_workforce_ledger_entry_id")
    private Long oldEarningWorkforceLedgerEntryId;
    @Column(name = "reversed_earning_workforce_ledger_entry_id")
    private Long reversedEarningWorkforceLedgerEntryId;
    @Column(name = "replacement_earning_workforce_ledger_entry_id")
    private Long replacementEarningWorkforceLedgerEntryId;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    protected ShiftCorrection() {
    }

    public ShiftCorrection(Long tenantId, Long shiftId, String idempotencyKey, String reason, Long actorUserId,
                           Long expectedShiftVersion, Shift shift, LocalDate newShiftDate, ShiftType newShiftType,
                           Long newSiteId, Long newDriverId, Long newEquipmentId, BigDecimal newDriverWage,
                           BigDecimal newPrice, BigDecimal newEquipmentOperatingCost, String newNotes) {
        this(tenantId, shiftId, idempotencyKey, reason, actorUserId, expectedShiftVersion, shift,
                newShiftDate, newShiftType, newSiteId, newDriverId, newEquipmentId, newDriverWage,
                newPrice, newEquipmentOperatingCost, newNotes, shift.getActualHours(), shift.getActualQuantity());
    }

    public ShiftCorrection(Long tenantId, Long shiftId, String idempotencyKey, String reason, Long actorUserId,
                           Long expectedShiftVersion, Shift shift, LocalDate newShiftDate, ShiftType newShiftType,
                           Long newSiteId, Long newDriverId, Long newEquipmentId, BigDecimal newDriverWage,
                           BigDecimal newPrice, BigDecimal newEquipmentOperatingCost, String newNotes,
                           BigDecimal newActualHours, BigDecimal newActualQuantity) {
        super(tenantId);
        this.shiftId = shiftId;
        this.idempotencyKey = idempotencyKey;
        this.reason = reason;
        this.status = "APPLYING";
        this.actorUserId = actorUserId;
        this.expectedShiftVersion = expectedShiftVersion;
        this.oldShiftDate = shift.getShiftDate();
        this.newShiftDate = newShiftDate;
        this.oldShiftType = shift.getShiftType();
        this.newShiftType = newShiftType;
        this.oldSiteId = shift.getSiteId();
        this.newSiteId = newSiteId;
        this.oldDriverId = shift.getDriverId();
        this.newDriverId = newDriverId;
        this.oldEquipmentId = shift.getEquipmentId();
        this.newEquipmentId = newEquipmentId;
        this.oldDriverWage = shift.getDriverWage();
        this.newDriverWage = newDriverWage;
        this.oldPrice = shift.getPrice();
        this.newPrice = newPrice;
        this.oldEquipmentOperatingCost = shift.getEquipmentOperatingCost();
        this.newEquipmentOperatingCost = newEquipmentOperatingCost;
        this.oldNotes = shift.getNotes();
        this.oldCompensationBasis = shift.getCompensationBasis();
        this.newCompensationBasis = shift.getCompensationBasis();
        this.oldCompensationRate = shift.getCompensationRate();
        this.oldCompensationUnitCode = shift.getCompensationUnitCode();
        this.newCompensationUnitCode = shift.getCompensationUnitCode();
        this.oldCompensationCurrencyCode = shift.getCompensationCurrencyCode();
        this.newCompensationCurrencyCode = shift.getCompensationCurrencyCode();
        this.oldCompensationCalculatedAmount = shift.getCompensationCalculatedAmount();
        this.newCompensationCalculatedAmount = newDriverWage;
        // Only a SHIFT legacy-rate override changes the legacy rate. Measured corrections
        // always preserve the historical unit rate, regardless of wage source.
        this.newCompensationRate = "SHIFT".equals(shift.getCompensationBasis())
                && "LEGACY_DRIVER_RATE".equals(shift.getWageSource())
                ? newDriverWage : shift.getCompensationRate();
        this.oldActualHours = shift.getActualHours();
        this.newActualHours = newActualHours;
        this.oldActualQuantity = shift.getActualQuantity();
        this.newActualQuantity = newActualQuantity;
        this.newNotes = newNotes;
        this.oldWageJournalEntryId = shift.getDriverWageJournalEntryId();
    }

    public Long getShiftId() { return shiftId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getReason() { return reason; }
    public String getStatus() { return status; }
    public Long getActorUserId() { return actorUserId; }
    public Long getExpectedShiftVersion() { return expectedShiftVersion; }
    public LocalDate getOldShiftDate() { return oldShiftDate; }
    public LocalDate getNewShiftDate() { return newShiftDate; }
    public ShiftType getOldShiftType() { return oldShiftType; }
    public ShiftType getNewShiftType() { return newShiftType; }
    public Long getOldSiteId() { return oldSiteId; }
    public Long getNewSiteId() { return newSiteId; }
    public Long getOldDriverId() { return oldDriverId; }
    public Long getNewDriverId() { return newDriverId; }
    public Long getOldEquipmentId() { return oldEquipmentId; }
    public Long getNewEquipmentId() { return newEquipmentId; }
    public BigDecimal getOldDriverWage() { return oldDriverWage; }
    public BigDecimal getNewDriverWage() { return newDriverWage; }
    public BigDecimal getOldPrice() { return oldPrice; }
    public BigDecimal getNewPrice() { return newPrice; }
    public BigDecimal getOldEquipmentOperatingCost() { return oldEquipmentOperatingCost; }
    public BigDecimal getNewEquipmentOperatingCost() { return newEquipmentOperatingCost; }
    public String getOldCompensationBasis() { return oldCompensationBasis; }
    public String getNewCompensationBasis() { return newCompensationBasis; }
    public BigDecimal getOldCompensationRate() { return oldCompensationRate; }
    public BigDecimal getNewCompensationRate() { return newCompensationRate; }
    public String getOldCompensationUnitCode() { return oldCompensationUnitCode; }
    public String getNewCompensationUnitCode() { return newCompensationUnitCode; }
    public String getOldCompensationCurrencyCode() { return oldCompensationCurrencyCode; }
    public String getNewCompensationCurrencyCode() { return newCompensationCurrencyCode; }
    public BigDecimal getOldCompensationCalculatedAmount() { return oldCompensationCalculatedAmount; }
    public BigDecimal getNewCompensationCalculatedAmount() { return newCompensationCalculatedAmount; }
    public BigDecimal getOldActualHours() { return oldActualHours; }
    public BigDecimal getNewActualHours() { return newActualHours; }
    public BigDecimal getOldActualQuantity() { return oldActualQuantity; }
    public BigDecimal getNewActualQuantity() { return newActualQuantity; }
    public String getOldNotes() { return oldNotes; }
    public String getNewNotes() { return newNotes; }
    public Long getOldWageJournalEntryId() { return oldWageJournalEntryId; }
    public Long getWageReversalJournalEntryId() { return wageReversalJournalEntryId; }
    public Long getReplacementWageJournalEntryId() { return replacementWageJournalEntryId; }
    public Long getOldEarningLedgerEntryId() { return oldEarningLedgerEntryId; }
    public Long getReversedEarningLedgerEntryId() { return reversedEarningLedgerEntryId; }
    public Long getReplacementEarningLedgerEntryId() { return replacementEarningLedgerEntryId; }
    public Long getOldEarningWorkforceLedgerEntryId() { return oldEarningWorkforceLedgerEntryId; }
    public Long getReversedEarningWorkforceLedgerEntryId() { return reversedEarningWorkforceLedgerEntryId; }
    public Long getReplacementEarningWorkforceLedgerEntryId() { return replacementEarningWorkforceLedgerEntryId; }
    public void setOldEarningLedgerEntryId(Long value) { this.oldEarningLedgerEntryId = value; }
    public void setWageReversalJournalEntryId(Long value) { this.wageReversalJournalEntryId = value; }
    public void setReplacementWageJournalEntryId(Long value) { this.replacementWageJournalEntryId = value; }
    public void setReversedEarningLedgerEntryId(Long value) { this.reversedEarningLedgerEntryId = value; }
    public void setReplacementEarningLedgerEntryId(Long value) { this.replacementEarningLedgerEntryId = value; }
    public void setOldEarningWorkforceLedgerEntryId(Long value) { this.oldEarningWorkforceLedgerEntryId = value; }
    public void setReversedEarningWorkforceLedgerEntryId(Long value) { this.reversedEarningWorkforceLedgerEntryId = value; }
    public void setReplacementEarningWorkforceLedgerEntryId(Long value) { this.replacementEarningWorkforceLedgerEntryId = value; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
}
