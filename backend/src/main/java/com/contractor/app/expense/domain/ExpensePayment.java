package com.contractor.app.expense.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "expense_payments")
public class ExpensePayment extends BaseTenantEntity {

    @Column(name = "expense_id", nullable = false, updatable = false)
    private Long expenseId;

    @Column(name = "payment_key", nullable = false, length = 120, updatable = false)
    private String paymentKey;

    @Column(name = "payment_date", nullable = false, updatable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "payment_account_id", nullable = false, updatable = false)
    private Long paymentAccountId;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private Long createdByUserId;

    protected ExpensePayment() {
    }

    public ExpensePayment(Long tenantId, Long expenseId, String paymentKey, LocalDate paymentDate,
                          BigDecimal amount, Long paymentAccountId, Long createdByUserId) {
        super(tenantId);
        this.expenseId = expenseId;
        this.paymentKey = paymentKey;
        this.paymentDate = paymentDate;
        this.amount = amount;
        this.paymentAccountId = paymentAccountId;
        this.createdByUserId = createdByUserId;
    }

    public void linkJournalEntry(Long journalEntryId) {
        if (this.journalEntryId != null || journalEntryId == null || journalEntryId <= 0) {
            throw new IllegalStateException("Expense payment journal link is already set or invalid.");
        }
        this.journalEntryId = journalEntryId;
    }

    public Long getExpenseId() { return expenseId; }
    public String getPaymentKey() { return paymentKey; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public BigDecimal getAmount() { return amount; }
    public Long getPaymentAccountId() { return paymentAccountId; }
    public Long getJournalEntryId() { return journalEntryId; }
    public Long getCreatedByUserId() { return createdByUserId; }
}
