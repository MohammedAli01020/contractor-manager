package com.contractor.app.accounting.api.dto;

import com.contractor.app.accounting.domain.AccountType;
import com.contractor.app.accounting.domain.ChartAccount;

public class ChartAccountResponse {

    private final Long id;
    private final String accountCode;
    private final String name;
    private final AccountType accountType;
    private final Long parentAccountId;
    private final boolean systemManaged;
    private final boolean active;

    public ChartAccountResponse(ChartAccount account) {
        this.id = account.getId();
        this.accountCode = account.getAccountCode();
        this.name = account.getName();
        this.accountType = account.getAccountType();
        this.parentAccountId = account.getParentAccountId();
        this.systemManaged = account.isSystemManaged();
        this.active = account.isActive();
    }

    public Long getId() {
        return id;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public String getName() {
        return name;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public Long getParentAccountId() {
        return parentAccountId;
    }

    public boolean isSystemManaged() {
        return systemManaged;
    }

    public boolean isActive() {
        return active;
    }
}
