package com.contractor.app.driver.api.dto;
import com.contractor.app.driver.domain.DriverLedgerEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
public class DriverLedgerEntryResponse {
    private final Long id, driverId, shiftId, journalEntryId;
    private final String transactionType, description;
    private final LocalDate transactionDate;
    private final BigDecimal amount, balanceEffect, balanceAfter;
    public DriverLedgerEntryResponse(DriverLedgerEntry e, BigDecimal balanceAfter) {
        this.id=e.getId(); this.driverId=e.getDriverId(); this.shiftId=e.getShiftId();
        this.journalEntryId=e.getJournalEntryId(); this.transactionType=e.getTransactionType();
        this.description=e.getDescription(); this.transactionDate=e.getTransactionDate();
        this.amount=e.getAmount(); this.balanceEffect=e.getBalanceEffect(); this.balanceAfter=balanceAfter;
    }
    public Long getId(){return id;} public Long getDriverId(){return driverId;} public Long getShiftId(){return shiftId;}
    public Long getJournalEntryId(){return journalEntryId;} public String getTransactionType(){return transactionType;}
    public String getDescription(){return description;} public LocalDate getTransactionDate(){return transactionDate;}
    public BigDecimal getAmount(){return amount;} public BigDecimal getBalanceEffect(){return balanceEffect;}
    public BigDecimal getBalanceAfter(){return balanceAfter;}
}