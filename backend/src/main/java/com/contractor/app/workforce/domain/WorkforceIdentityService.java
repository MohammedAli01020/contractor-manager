package com.contractor.app.workforce.domain;

import com.contractor.app.common.error.ApiException;
import com.contractor.app.driver.domain.Driver;
import com.contractor.app.workforce.infra.WorkforceMemberRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns canonical workforce identity writes while legacy operational profiles
 * remain compatible mirrors during the migration period.
 */
@Service
public class WorkforceIdentityService {

    private final WorkforceMemberRepository members;

    public WorkforceIdentityService(WorkforceMemberRepository members) {
        this.members = members;
    }

    @Transactional
    public WorkforceMember createForDriver(Driver driver) {
        WorkforceMember member = new WorkforceMember(driver.getTenantId(), driver.getName());
        copyIdentity(driver, member);
        return members.saveAndFlush(member);
    }

    /**
     * Caller must lock the operational Driver row before this method. This
     * preserves the global lock order: Driver, then WorkforceMember.
     */
    @Transactional
    public WorkforceMember synchronizeFromDriver(Driver driver) {
        WorkforceMember member = members.findByIdAndTenantIdForUpdate(
                        driver.getWorkforceMemberId(), driver.getTenantId())
                .orElseThrow(() -> ApiException.conflict(
                        "تعذر العثور على الهوية العامة المرتبطة بالسائق. أوقف العملية وراجع ربط بيانات العامل."));
        if (identityChanged(driver, member)) {
            copyIdentity(driver, member);
            return members.saveAndFlush(member);
        }
        return member;
    }

    @Transactional(readOnly = true)
    public WorkforceMember requireForDriver(Long tenantId, Long workforceMemberId) {
        return members.findByIdAndTenantId(workforceMemberId, tenantId)
                .orElseThrow(() -> ApiException.conflict(
                        "تعذر العثور على الهوية العامة المرتبطة بالسجل التشغيلي. أوقف العملية وراجع ربط بيانات العامل."));
    }

    private static boolean identityChanged(Driver driver, WorkforceMember member) {
        return !Objects.equals(member.getFullName(), driver.getName())
                || !Objects.equals(member.getPhone(), driver.getPhone())
                || !Objects.equals(member.getNationalId(), driver.getNationalId())
                || !Objects.equals(member.getNotes(), driver.getNotes())
                || member.isActive() != driver.isActive();
    }

    private static void copyIdentity(Driver driver, WorkforceMember member) {
        member.setFullName(driver.getName());
        member.setPhone(driver.getPhone());
        member.setNationalId(driver.getNationalId());
        member.setNotes(driver.getNotes());
        member.setActive(driver.isActive());
    }
}
