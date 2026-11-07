package com.contractor.app.workforce.domain;

import com.contractor.app.audit.domain.AuditAction;
import com.contractor.app.audit.domain.AuditEntityType;
import com.contractor.app.audit.domain.AuditService;
import com.contractor.app.common.error.ApiException;
import com.contractor.app.common.util.Texts;
import com.contractor.app.driver.domain.Driver;
import com.contractor.app.driver.infra.DriverRepository;
import com.contractor.app.workforce.api.dto.WorkforceMemberRequest;
import com.contractor.app.workforce.api.dto.WorkforceMemberResponse;
import com.contractor.app.workforce.infra.WorkforceMemberRepository;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns canonical workforce identity writes. Legacy driver rows are synchronized
 * in the same transaction while the migration keeps their operational IDs.
 */
@Service
public class WorkforceIdentityService {

    private final WorkforceMemberRepository members;
    private final DriverRepository drivers;
    private final AuditService audit;

    public WorkforceIdentityService(WorkforceMemberRepository members, DriverRepository drivers,
                                    AuditService audit) {
        this.members = members;
        this.drivers = drivers;
        this.audit = audit;
    }

    @Transactional
    public WorkforceMember createMember(Long tenantId, WorkforceMemberRequest request) {
        WorkforceMember member = new WorkforceMember(tenantId, request.getFullName().trim());
        applyIdentity(request, member);
        WorkforceMember saved = members.saveAndFlush(member);
        audit.record(tenantId, AuditEntityType.WORKFORCE_MEMBER, saved.getId(),
                AuditAction.CREATED, null, null, "fullName=" + saved.getFullName());
        return saved;
    }

    @Transactional(readOnly = true)
    public WorkforceMember requireMember(Long tenantId, Long memberId) {
        return members.findByIdAndTenantId(memberId, tenantId)
                .orElseThrow(() -> ApiException.notFound("الشخص غير موجود في القوى العاملة."));
    }

    /**
     * Lock order is always legacy Driver first, then WorkforceMember. Driver
     * operations already use this order; matching it here avoids deadlocks.
     */
    @Transactional
    public WorkforceMember updateMember(Long tenantId, Long memberId, WorkforceMemberRequest request) {
        Driver linkedDriver = drivers.findByWorkforceMemberIdAndTenantIdForUpdate(memberId, tenantId)
                .orElse(null);
        WorkforceMember member = members.findByIdAndTenantIdForUpdate(memberId, tenantId)
                .orElseThrow(() -> ApiException.notFound("الشخص غير موجود في القوى العاملة."));
        member.requireVersion(request.getVersion());

        String oldName = member.getFullName();
        String oldPhone = member.getPhone();
        String oldNationalId = member.getNationalId();
        String oldNotes = member.getNotes();

        applyIdentity(request, member);
        if (linkedDriver != null) {
            // Keep legacy operational projections aligned with the canonical identity.
            linkedDriver.setName(member.getFullName());
            linkedDriver.setPhone(member.getPhone());
            linkedDriver.setNationalId(member.getNationalId());
            linkedDriver.setNotes(member.getNotes());
            drivers.saveAndFlush(linkedDriver);
        }
        WorkforceMember saved = members.saveAndFlush(member);

        AuditService.ChangeSet changes = new AuditService.ChangeSet()
                .track("fullName", oldName, saved.getFullName())
                .track("phone", oldPhone, saved.getPhone())
                .track("nationalId", oldNationalId, saved.getNationalId())
                .track("notes", oldNotes, saved.getNotes());
        audit.recordChanges(tenantId, AuditEntityType.WORKFORCE_MEMBER, memberId, changes);
        return saved;
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
    public List<WorkforceMember> findAllForTenant(Long tenantId, Collection<Long> memberIds) {
        return members.findAllByTenantIdAndIdIn(tenantId, memberIds);
    }

    @Transactional(readOnly = true)
    public WorkforceMember requireForDriver(Long tenantId, Long workforceMemberId) {
        return members.findByIdAndTenantId(workforceMemberId, tenantId)
                .orElseThrow(() -> ApiException.conflict(
                        "تعذر العثور على الهوية العامة المرتبطة بالسجل التشغيلي. أوقف العملية وراجع ربط بيانات العامل."));
    }

    private static void applyIdentity(WorkforceMemberRequest request, WorkforceMember member) {
        member.setFullName(request.getFullName().trim());
        member.setPhone(Texts.blankToNull(request.getPhone()));
        member.setNationalId(Texts.blankToNull(request.getNationalId()));
        member.setNotes(Texts.blankToNull(request.getNotes()));
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
