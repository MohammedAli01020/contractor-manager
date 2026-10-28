package com.contractor.app.accounting.domain;

import com.contractor.app.accounting.infra.ChartAccountRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChartOfAccountsQueryService {

    private final ChartAccountRepository accounts;

    public ChartOfAccountsQueryService(ChartAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public List<ChartAccount> listForTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("A persisted tenant ID is required to list accounts.");
        }
        return accounts.findByTenantIdOrderByAccountCodeAsc(tenantId);
    }
}
