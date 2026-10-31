package com.contractor.app.accounting.domain;

import com.contractor.app.accounting.infra.ChartAccountRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the tenant's baseline accounts without overwriting accounts that already exist.
 * Account codes are stable identifiers; names can be customized later by the tenant.
 */
@Service
public class DefaultChartOfAccountsService {

    private static final List<AccountTemplate> DEFAULT_ACCOUNTS = Arrays.asList(
            new AccountTemplate("1000", "الخزينة", AccountType.ASSET),
            new AccountTemplate("1010", "البنوك", AccountType.ASSET),
            new AccountTemplate("1100", "العملاء والمستحقات", AccountType.ASSET),
            new AccountTemplate("1200", "الأصول الثابتة", AccountType.ASSET),
            new AccountTemplate("1300", "سلف العاملين القابلة للاسترداد", AccountType.ASSET),
            new AccountTemplate("2000", "الموردون", AccountType.LIABILITY),
            new AccountTemplate("2100", "مستحقات السائقين", AccountType.LIABILITY),
            new AccountTemplate("2200", "مستحقات الملاك والشركاء", AccountType.LIABILITY),
            new AccountTemplate("3000", "رأس المال", AccountType.EQUITY),
            new AccountTemplate("4000", "إيرادات الورديات", AccountType.REVENUE),
            new AccountTemplate("4100", "إيرادات أخرى", AccountType.REVENUE),
            new AccountTemplate("5000", "مصروف أجور السائقين", AccountType.EXPENSE),
            new AccountTemplate("5100", "مصروفات تشغيل المعدات", AccountType.EXPENSE),
            new AccountTemplate("5200", "مصروفات أخرى", AccountType.EXPENSE)
    );

    private final ChartAccountRepository accounts;

    public DefaultChartOfAccountsService(ChartAccountRepository accounts) {
        this.accounts = accounts;
    }

    /**
     * Idempotently adds missing baseline accounts for one tenant. Existing account names,
     * active flags and other tenant-specific edits are intentionally preserved.
     */
    @Transactional
    public void initializeForTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("A persisted tenant ID is required to initialize accounts.");
        }

        Set<String> existingCodes = accounts.findByTenantIdOrderByAccountCodeAsc(tenantId).stream()
                .map(ChartAccount::getAccountCode)
                .filter(code -> code != null)
                .map(code -> code.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));

        List<ChartAccount> missing = new ArrayList<>();
        for (AccountTemplate template : DEFAULT_ACCOUNTS) {
            if (!existingCodes.contains(template.code)) {
                missing.add(new ChartAccount(tenantId, template.code, template.name, template.type, null, true));
            }
        }

        if (!missing.isEmpty()) {
            accounts.saveAll(missing);
        }
    }

    private static final class AccountTemplate {
        private final String code;
        private final String name;
        private final AccountType type;

        private AccountTemplate(String code, String name, AccountType type) {
            this.code = code;
            this.name = name;
            this.type = type;
        }
    }
}
