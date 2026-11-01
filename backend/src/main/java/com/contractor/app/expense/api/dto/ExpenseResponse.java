package com.contractor.app.expense.api.dto;
import com.contractor.app.expense.domain.Expense;
import java.math.BigDecimal;
import java.time.LocalDate;
public class ExpenseResponse {
 private final Long id; private final String expenseNumber; private final LocalDate expenseDate;
 private final String categoryCode; private final String description; private final String supplierName;
 private final BigDecimal amount, paidAmount, remainingAmount; private final Long expenseAccountId;
 private final Long equipmentId, shiftId, journalEntryId;
 public ExpenseResponse(Expense e, BigDecimal paid) {
  id=e.getId(); expenseNumber=e.getExpenseNumber(); expenseDate=e.getExpenseDate();
  categoryCode=e.getCategoryCode(); description=e.getDescription(); supplierName=e.getSupplierName();
  amount=e.getAmount(); paidAmount=paid; remainingAmount=amount.subtract(paid);
  expenseAccountId=e.getExpenseAccountId(); equipmentId=e.getEquipmentId(); shiftId=e.getShiftId();
  journalEntryId=e.getJournalEntryId();
 }
 public Long getId(){return id;} public String getExpenseNumber(){return expenseNumber;}
 public LocalDate getExpenseDate(){return expenseDate;} public String getCategoryCode(){return categoryCode;}
 public String getDescription(){return description;} public String getSupplierName(){return supplierName;}
 public BigDecimal getAmount(){return amount;} public BigDecimal getPaidAmount(){return paidAmount;}
 public BigDecimal getRemainingAmount(){return remainingAmount;} public Long getExpenseAccountId(){return expenseAccountId;}
 public Long getEquipmentId(){return equipmentId;} public Long getShiftId(){return shiftId;}
 public Long getJournalEntryId(){return journalEntryId;}
}