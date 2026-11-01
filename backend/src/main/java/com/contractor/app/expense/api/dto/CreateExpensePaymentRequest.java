package com.contractor.app.expense.api.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
public class CreateExpensePaymentRequest {
 @NotBlank private String paymentKey;
 @NotNull private LocalDate paymentDate;
 @NotNull @DecimalMin("0.01") private BigDecimal amount;
 @NotNull @Positive private Long paymentAccountId;
 public String getPaymentKey(){return paymentKey;}
 public LocalDate getPaymentDate(){return paymentDate;}
 public BigDecimal getAmount(){return amount;}
 public Long getPaymentAccountId(){return paymentAccountId;}
}