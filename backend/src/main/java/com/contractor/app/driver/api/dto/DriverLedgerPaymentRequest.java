package com.contractor.app.driver.api.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.validation.constraints.*;
public class DriverLedgerPaymentRequest {
    @NotBlank private String clientRequestKey;
    @NotNull private LocalDate paymentDate;
    @NotNull @DecimalMin("0.01") private BigDecimal amount;
    @NotNull @Positive private Long paymentAccountId;
    @NotBlank private String description;
    public String getClientRequestKey(){return clientRequestKey;}
    public LocalDate getPaymentDate(){return paymentDate;}
    public BigDecimal getAmount(){return amount;}
    public Long getPaymentAccountId(){return paymentAccountId;}
    public String getDescription(){return description;}
}