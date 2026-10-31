package com.contractor.app.accounting.domain;

/** Stable identifiers for platform-managed accounts; database IDs remain tenant-specific. */
public enum SystemAccountCode {
    CASH("1000", "الخزينة", AccountType.ASSET, true),
    BANK("1010", "البنوك", AccountType.ASSET, true),
    RECEIVABLES("1100", "العملاء والمستحقات", AccountType.ASSET, false),
    FIXED_ASSETS("1200", "الأصول الثابتة", AccountType.ASSET, false),
    RECOVERABLE_WORKER_ADVANCES("1300", "سلف العاملين القابلة للاسترداد", AccountType.ASSET, false),
    INVENTORY("1400", "المخزون", AccountType.ASSET, false),
    SUPPLIER_PAYABLE("2000", "الموردون", AccountType.LIABILITY, false),
    DRIVER_PAYABLE("2100", "مستحقات السائقين", AccountType.LIABILITY, false),
    OWNER_PARTNER_PAYABLE("2200", "مستحقات الملاك والشركاء", AccountType.LIABILITY, false),
    CAPITAL("3000", "رأس المال", AccountType.EQUITY, false),
    SHIFT_REVENUE("4000", "إيرادات الورديات", AccountType.REVENUE, false),
    OTHER_REVENUE("4100", "إيرادات أخرى", AccountType.REVENUE, false),
    DRIVER_WAGE_EXPENSE("5000", "مصروف أجور السائقين", AccountType.EXPENSE, false),
    EQUIPMENT_OPERATING_EXPENSE("5100", "مصروفات تشغيل المعدات", AccountType.EXPENSE, false),
    GENERAL_EXPENSE("5200", "مصروفات أخرى", AccountType.EXPENSE, false);

    private final String code;
    private final String defaultName;
    private final AccountType accountType;
    private final boolean cashBankAccount;

    SystemAccountCode(String code, String defaultName, AccountType accountType, boolean cashBankAccount) {
        this.code = code;
        this.defaultName = defaultName;
        this.accountType = accountType;
        this.cashBankAccount = cashBankAccount;
    }
    public String getCode() { return code; }
    public String getDefaultName() { return defaultName; }
    public AccountType getAccountType() { return accountType; }
    public boolean isCashBankAccount() { return cashBankAccount; }
}
