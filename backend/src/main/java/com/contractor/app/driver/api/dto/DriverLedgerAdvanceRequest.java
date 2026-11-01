package com.contractor.app.driver.api.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.validation.constraints.*;
public class DriverLedgerAdvanceRequest {
    @NotBlank private String clientRequestKey;
    @NotNull private LocalDate advanceDate;
    @NotNull @DecimalMin("0.01") private BigDecimal amount;
    @NotNull @Positive private Long paymentAccountId;
    @NotBlank private String description;
    @NotBlank private String advanceType;
    public String getClientRequestKey(){return clientRequestKey;}
    public LocalDate getAdvanceDate(){return advanceDate;}
    public BigDecimal getAmount(){return amount;}
    public Long getPaymentAccountId(){return paymentAccountId;}
    public String getDescription(){return description;}
    public String getAdvanceType(){return advanceType;}
}