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

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    protected ShiftCorrection() {
    }

    public ShiftCorrection(Long tenantId, Long shiftId, String idempotencyKey, String reason, Long actorUserId,
                           Long expectedShiftVersion, Shift shift, LocalDate newShiftDate, ShiftType newShiftType,
                           Long newSiteId, Long newDriverId, Long newEquipmentId, BigDecimal newDriverWage,
                           BigDecimal newPrice, BigDecimal newEquipmentOperatingCost, String newNotes) {
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
        this.newNotes = newNotes;
        this.oldWageJournalEntryId = shift.getDriverWageJournalEntryId();
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
    public String getOldNotes() { return oldNotes; }
    public String getNewNotes() { return newNotes; }
    public Long getOldWageJournalEntryId() { return oldWageJournalEntryId; }
    public Long getWageReversalJournalEntryId() { return wageReversalJournalEntryId; }
    public Long getReplacementWageJournalEntryId() { return replacementWageJournalEntryId; }
    public Long getOldEarningLedgerEntryId() { return oldEarningLedgerEntryId; }
    public Long getReversedEarningLedgerEntryId() { return reversedEarningLedgerEntryId; }
    public Long getReplacementEarningLedgerEntryId() { return replacementEarningLedgerEntryId; }
    public void setOldEarningLedgerEntryId(Long value) { this.oldEarningLedgerEntryId = value; }
    public void setWageReversalJournalEntryId(Long value) { this.wageReversalJournalEntryId = value; }
    public void setReplacementWageJournalEntryId(Long value) { this.replacementWageJournalEntryId = value; }
    public void setReversedEarningLedgerEntryId(Long value) { this.reversedEarningLedgerEntryId = value; }
    public void setReplacementEarningLedgerEntryId(Long value) { this.replacementEarningLedgerEntryId = value; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
}
