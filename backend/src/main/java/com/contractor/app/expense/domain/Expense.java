package com.contractor.app.expense.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "expenses")
public class Expense extends BaseTenantEntity {
    @Column(name = "expense_number", nullable = false, length = 60)
    private String expenseNumber;
    @Column(name = "client_request_key", nullable = false, length = 120, updatable = false)
    private String clientRequestKey;
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;
    @Column(name = "category_code", nullable = false, length = 40)
    private String categoryCode;
    @Column(nullable = false, length = 300)
    private String description;
    @Column(name = "supplier_name", length = 150)
    private String supplierName;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Column(name = "expense_account_id", nullable = false)
    private Long expenseAccountId;
    @Column(name = "equipment_id")
    private Long equipmentId;
    @Column(name = "shift_id")
    private Long shiftId;
    @Column(name = "journal_entry_id")
    private Long journalEntryId;
    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private Long createdByUserId;

    protected Expense() {}

    public Expense(Long tenantId, String expenseNumber, String clientRequestKey, LocalDate expenseDate,
                   String categoryCode, String description, String supplierName, BigDecimal amount,
                   Long expenseAccountId, Long equipmentId, Long shiftId, Long createdByUserId) {
        super(tenantId);
        this.expenseNumber=expenseNumber; this.clientRequestKey=clientRequestKey;
        this.expenseDate=expenseDate; this.categoryCode=categoryCode; this.description=description;
        this.supplierName=supplierName; this.amount=amount; this.expenseAccountId=expenseAccountId;
        this.equipmentId=equipmentId; this.shiftId=shiftId; this.createdByUserId=createdByUserId;
    }
    public void linkJournalEntry(Long id) {
        if (journalEntryId!=null || id==null || id<=0) throw new IllegalStateException("Expense journal link is already set or invalid.");
        journalEntryId=id;
    }
    public String getExpenseNumber(){return expenseNumber;}
    public String getClientRequestKey(){return clientRequestKey;}
    public LocalDate getExpenseDate(){return expenseDate;}
    public String getCategoryCode(){return categoryCode;}
    public String getDescription(){return description;}
    public String getSupplierName(){return supplierName;}
    public BigDecimal getAmount(){return amount;}
    public Long getExpenseAccountId(){return expenseAccountId;}
    public Long getEquipmentId(){return equipmentId;}
    public Long getShiftId(){return shiftId;}
    public Long getJournalEntryId(){return journalEntryId;}
    public Long getCreatedByUserId(){return createdByUserId;}
}
