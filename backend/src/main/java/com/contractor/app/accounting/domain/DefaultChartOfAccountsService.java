package com.contractor.app.accounting.domain;

import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.contractor.app.common.error.ApiException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates missing tenant defaults and resolves system accounts for journal posting. */
@Service
public class DefaultChartOfAccountsService {
    private final ChartAccountRepository accounts;

    public DefaultChartOfAccountsService(ChartAccountRepository accounts) {
        this.accounts = accounts;
    }

    /** Adds missing baseline accounts without overwriting tenant edits or custom accounts. */
    @Transactional
    public void initializeForTenant(Long tenantId) {
        requireTenantId(tenantId);
        Set<String> existingCodes = accounts.findByTenantIdOrderByAccountCodeAsc(tenantId).stream()
                .map(ChartAccount::getAccountCode)
                .filter(code -> code != null)
                .map(code -> code.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));

        List<ChartAccount> missing = new ArrayList<>();
        for (SystemAccountCode template : SystemAccountCode.values()) {
            if (!existingCodes.contains(template.getCode())) {
                missing.add(new ChartAccount(tenantId, template.getCode(), template.getDefaultName(),
                        template.getAccountType(), null, true, template.isCashBankAccount()));
            }
        }
        if (!missing.isEmpty()) {
            accounts.saveAll(missing);
        }
    }

    /**
     * Resolves a system account within one tenant and validates its immutable accounting role
     * before a business service uses its tenant-local ID in a journal.
     */
    @Transactional(readOnly = true)
    public ChartAccount requireSystemAccount(Long tenantId, SystemAccountCode systemCode) {
        requireTenantId(tenantId);
        if (systemCode == null) {
            throw ApiException.badRequest("نوع الحساب الافتراضي مطلوب.");
        }
        ChartAccount account = accounts.findByTenantIdAndAccountCodeIgnoreCase(tenantId, systemCode.getCode())
                .filter(ChartAccount::isActive)
                .orElseThrow(() -> ApiException.conflict(
                        "الحساب الافتراضي " + systemCode.getCode() + " غير مهيأ أو معطّل."));
        if (!account.isSystemManaged()
                || account.getAccountType() != systemCode.getAccountType()
                || account.isCashBankAccount() != systemCode.isCashBankAccount()) {
            throw ApiException.conflict(
                    "إعداد الحساب الافتراضي " + systemCode.getCode() + " غير متوافق مع دليل الحسابات.");
        }
        return account;
    }

    private void requireTenantId(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw ApiException.badRequest("معرّف الشركة مطلوب لتهيئة أو قراءة دليل الحسابات.");
        }
    }
}
