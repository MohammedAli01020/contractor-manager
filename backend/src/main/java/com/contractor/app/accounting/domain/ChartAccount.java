package com.contractor.app.accounting.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

@Entity
@Table(name = "chart_of_accounts")
public class ChartAccount extends BaseTenantEntity {

    @Column(name = "account_code", nullable = false, length = 30)
    private String accountCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(name = "parent_account_id")
    private Long parentAccountId;

    @Column(name = "system_managed", nullable = false)
    private boolean systemManaged;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "cash_bank_account", nullable = false)
    private boolean cashBankAccount;

    protected ChartAccount() {
    }

    public ChartAccount(Long tenantId, String accountCode, String name, AccountType accountType,
                        Long parentAccountId, boolean systemManaged) {
        this(tenantId, accountCode, name, accountType, parentAccountId, systemManaged,
                "1000".equals(accountCode) || "1010".equals(accountCode));
    }

    public ChartAccount(Long tenantId, String accountCode, String name, AccountType accountType,
                        Long parentAccountId, boolean systemManaged, boolean cashBankAccount) {
        super(tenantId);
        if (cashBankAccount && accountType != AccountType.ASSET) {
            throw new IllegalArgumentException("Cash/bank accounts must use ASSET account type.");
        }
        this.accountCode = accountCode;
        this.name = name;
        this.accountType = accountType;
        this.parentAccountId = parentAccountId;
        this.systemManaged = systemManaged;
        this.cashBankAccount = cashBankAccount;
        this.active = true;
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

    public boolean isCashBankAccount() {
        return cashBankAccount;
    }
}
