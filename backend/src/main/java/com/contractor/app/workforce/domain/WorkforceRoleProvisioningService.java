package com.contractor.app.workforce.domain;

import com.contractor.app.workforce.infra.WorkforceRoleRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkforceRoleProvisioningService {

    private static final List<String[]> DEFAULT_ROLES = Arrays.asList(
            new String[] {"DRIVER", "سائق"},
            new String[] {"EQUIPMENT_OPERATOR", "مشغل معدة"},
            new String[] {"WORKER", "عامل"},
            new String[] {"TECHNICIAN", "فني"},
            new String[] {"SUPERVISOR", "مشرف"},
            new String[] {"ENGINEER", "مهندس"},
            new String[] {"ADMINISTRATIVE", "إداري"}
    );

    private final WorkforceRoleRepository roles;

    public WorkforceRoleProvisioningService(WorkforceRoleRepository roles) {
        this.roles = roles;
    }

    @Transactional
    public void initializeForTenant(Long tenantId) {
        for (String[] definition : DEFAULT_ROLES) {
            if (!roles.existsByTenantIdAndCode(tenantId, definition[0])) {
                WorkforceRole role = new WorkforceRole(tenantId, definition[0], definition[1]);
                role.setSystemDefined(true);
                roles.save(role);
            }
        }
        roles.flush();
    }
}
