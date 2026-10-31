package com.contractor.app.expense.api.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
public class CreateExpenseRequest {
 @NotBlank private String clientRequestKey;
 @NotNull private LocalDate expenseDate;
 @NotBlank private String categoryCode;
 @NotBlank private String description;
 private String supplierName;
 @NotNull @DecimalMin("0.01") private BigDecimal amount;
 @NotNull @Positive private Long expenseAccountId;
 private Long equipmentId;
 private Long shiftId;
 public String getClientRequestKey(){return clientRequestKey;}
 public LocalDate getExpenseDate(){return expenseDate;}
 public String getCategoryCode(){return categoryCode;}
 public String getDescription(){return description;}
 public String getSupplierName(){return supplierName;}
 public BigDecimal getAmount(){return amount;}
 public Long getExpenseAccountId(){return expenseAccountId;}
 public Long getEquipmentId(){return equipmentId;}
 public Long getShiftId(){return shiftId;}
}