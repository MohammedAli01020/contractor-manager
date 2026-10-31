package com.contractor.app.accounting.domain;

import com.contractor.app.accounting.infra.ChartAccountRepository;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.tenant.domain.Tenant;
import com.contractor.app.tenant.infra.TenantRepository;
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
    private final TenantRepository tenants;

    public DefaultChartOfAccountsService(ChartAccountRepository accounts, TenantRepository tenants) {
        this.accounts = accounts;
        this.tenants = tenants;
    }

    /** Adds missing baseline accounts without overwriting tenant edits or custom accounts. */
    @Transactional
    public void initializeForTenant(Long tenantId) {
        requireTenantId(tenantId);
        Set<String> existingCodes = existingCodes(tenantId);
        if (missingAccounts(tenantId, existingCodes).isEmpty()) {
            return;
        }

        // Lock only while defaults are missing. Normal shift completion does not serialize
        // on the tenant row once its chart has been initialized.
        tenants.findByIdForUpdate(tenantId)
                .orElseThrow(() -> ApiException.notFound("الشركة غير موجودة لتهيئة دليل الحسابات."));

        // Another request may have initialized the chart while this transaction waited.
        existingCodes = existingCodes(tenantId);
        List<ChartAccount> missing = missingAccounts(tenantId, existingCodes);
        if (!missing.isEmpty()) {
            accounts.saveAll(missing);
        }
    }


    private Set<String> existingCodes(Long tenantId) {
        return accounts.findByTenantIdOrderByAccountCodeAsc(tenantId).stream()
                .map(ChartAccount::getAccountCode)
                .filter(code -> code != null)
                .map(code -> code.toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private List<ChartAccount> missingAccounts(Long tenantId, Set<String> existingCodes) {
        List<ChartAccount> missing = new ArrayList<>();
        for (SystemAccountCode template : SystemAccountCode.values()) {
            if (!existingCodes.contains(template.getCode())) {
                missing.add(new ChartAccount(tenantId, template.getCode(), template.getDefaultName(),
                        template.getAccountType(), null, true, template.isCashBankAccount()));
            }
        }
        return missing;
    }

    /**
     * Resolves a system account within one tenant and validates its immutable accounting role
     * before a business service uses its tenant-local ID in a journal.
     */
    @Transactional(readOnly = true)
    public ChartAccount requireSystemAccount(Long tenantId, SystemAccountCode systemCode) {
        if (tenantId == null || tenantId <= 0) {
            throw ApiException.badRequest("معرّف الشركة مطلوب لقراءة دليل الحسابات.");
        }
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
            throw new IllegalArgumentException("A persisted tenant ID is required to initialize accounts.");
        }
    }
}
