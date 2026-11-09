package com.contractor.app.workforce.domain;

import com.contractor.app.common.error.ApiException;
import com.contractor.app.driver.domain.Driver;
import com.contractor.app.driver.domain.WorkerType;
import com.contractor.app.driver.infra.DriverRepository;
import java.math.BigDecimal;
import com.contractor.app.site.domain.Site;
import com.contractor.app.site.infra.SiteRepository;
import com.contractor.app.tenant.infra.TenantRepository;
import com.contractor.app.workforce.api.dto.EndWorkforceAssignmentRequest;
import com.contractor.app.workforce.api.dto.WorkforceAssignmentRequest;
import com.contractor.app.workforce.api.dto.WorkforceAssignmentResponse;
import com.contractor.app.workforce.api.dto.WorkforceRoleRequest;
import com.contractor.app.workforce.api.dto.WorkforceRoleSummaryResponse;
import com.contractor.app.workforce.api.dto.WorkforceRoleResponse;
import com.contractor.app.workforce.infra.WorkforceAssignmentRepository;
import com.contractor.app.workforce.infra.WorkforceMemberRepository;
import com.contractor.app.workforce.infra.WorkforceRoleRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkforceAssignmentService {

    private final WorkforceMemberRepository members;
    private final WorkforceRoleRepository roles;
    private final WorkforceAssignmentRepository assignments;
    private final SiteRepository sites;
    private final TenantRepository tenants;
    private final DriverRepository drivers;

    public WorkforceAssignmentService(WorkforceMemberRepository members,
                                      WorkforceRoleRepository roles,
                                      WorkforceAssignmentRepository assignments,
                                      SiteRepository sites,
                                      TenantRepository tenants, DriverRepository drivers) {
        this.members = members;
        this.roles = roles;
        this.assignments = assignments;
        this.sites = sites;
        this.tenants = tenants;
        this.drivers = drivers;
    }

    @Transactional(readOnly = true)
    public List<WorkforceRoleResponse> listRoles(Long tenantId, boolean includeInactive) {
        return roles.findAllByTenantIdOrderByNameAsc(tenantId).stream()
                .filter(role -> includeInactive || role.isActive())
                .map(WorkforceRoleResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<Long, List<WorkforceRoleSummaryResponse>> activeRoleSummaries(
            Long tenantId, List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<WorkforceAssignment> activeAssignments =
                assignments.findAllByTenantIdAndWorkforceMemberIdInAndStatusOrderByEffectiveFromDescIdDesc(
                        tenantId, memberIds, WorkforceAssignmentStatus.ACTIVE);
        if (activeAssignments.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> roleIds = activeAssignments.stream()
                .map(WorkforceAssignment::getWorkforceRoleId).distinct().collect(Collectors.toList());
        Map<Long, WorkforceRole> rolesById = roles.findAllByTenantIdAndIdIn(tenantId, roleIds).stream()
                .collect(Collectors.toMap(WorkforceRole::getId, role -> role));
        Map<Long, List<WorkforceRoleSummaryResponse>> result = new HashMap<>();
        for (WorkforceAssignment assignment : activeAssignments) {
            WorkforceRole role = rolesById.get(assignment.getWorkforceRoleId());
            if (role != null) {
                result.computeIfAbsent(assignment.getWorkforceMemberId(), ignored -> new ArrayList<>())
                        .add(new WorkforceRoleSummaryResponse(assignment, role));
            }
        }
        return result;
    }

    @Transactional
    public WorkforceRoleResponse createRole(Long tenantId, WorkforceRoleRequest request) {
        // Use the same tenant-first lock as default-role provisioning so concurrent
        // role creation requests cannot both pass the existence check.
        tenants.findByIdForUpdate(tenantId)
                .orElseThrow(() -> ApiException.notFound("الشركة غير موجودة."));
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        String name = request.getName().trim();
        if (roles.existsByTenantIdAndCode(tenantId, code)) {
            throw ApiException.conflict("كود الدور مستخدم بالفعل داخل الشركة.");
        }
        WorkforceRole role = new WorkforceRole(tenantId, code, name);
        boolean wasEligible = role.isShiftEligible();
        boolean nowEligible = Boolean.TRUE.equals(request.getShiftEligible());
        role.setShiftEligible(nowEligible);
        WorkforceRole saved = roles.saveAndFlush(role);
        if (!wasEligible && nowEligible) {
            // Existing active assignments must become usable immediately when an
            // administrator enables shift eligibility for this role.
            List<WorkforceAssignment> activeAssignments = assignments
                    .findAllByTenantIdAndWorkforceRoleIdAndStatus(
                            tenantId, roleId, WorkforceAssignmentStatus.ACTIVE);
            for (WorkforceAssignment assignment : activeAssignments) {
                Driver linked = drivers.findByWorkforceMemberIdAndTenantIdForUpdate(
                        assignment.getWorkforceMemberId(), tenantId).orElse(null);
                WorkforceMember member = lockMember(tenantId, assignment.getWorkforceMemberId());
                if (member.isActive()) ensureOperationalDriver(member, saved, linked);
            }
        }
        return WorkforceRoleResponse.from(saved);
    }

    @Transactional
    public WorkforceRoleResponse updateShiftEligibility(Long tenantId, Long roleId,
            com.contractor.app.workforce.api.dto.WorkforceRoleEligibilityRequest request) {
        WorkforceRole role = roles.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> ApiException.notFound("الدور الوظيفي غير موجود."));
        role.requireVersion(request.getVersion());
        if (!role.isActive() && Boolean.TRUE.equals(request.getShiftEligible())) {
            throw ApiException.conflict("لا يمكن تفعيل أهلية الوردية لدور غير نشط.");
        }
        boolean wasEligible = role.isShiftEligible();
        boolean nowEligible = Boolean.TRUE.equals(request.getShiftEligible());
        role.setShiftEligible(nowEligible);
        WorkforceRole saved = roles.saveAndFlush(role);
        if (!wasEligible && nowEligible) {
            // Existing active assignments become shift candidates immediately.
            List<WorkforceAssignment> activeAssignments =
                    assignments.findAllByTenantIdAndWorkforceRoleIdAndStatus(
                            tenantId, roleId, WorkforceAssignmentStatus.ACTIVE);
            for (WorkforceAssignment assignment : activeAssignments) {
                Driver linked = drivers.findByWorkforceMemberIdAndTenantIdForUpdate(
                        assignment.getWorkforceMemberId(), tenantId).orElse(null);
                WorkforceMember member = lockMember(tenantId, assignment.getWorkforceMemberId());
                if (member.isActive()) {
                    ensureOperationalDriver(member, saved, linked);
                }
            }
        }
        return WorkforceRoleResponse.from(saved);
    }

    @Transactional
    public void deactivateRole(Long tenantId, Long roleId) {
        WorkforceRole role = roles.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> ApiException.notFound("الدور الوظيفي غير موجود."));
        if (role.isSystemDefined()) {
            throw ApiException.conflict("لا يمكن تعطيل دور أساسي في النظام.");
        }
        role.setActive(false);
        roles.saveAndFlush(role);
    }

    @Transactional(readOnly = true)
    public List<WorkforceAssignmentResponse> listAssignments(Long tenantId, Long memberId) {
        requireMember(tenantId, memberId);
        return assignments.findAllByTenantIdAndWorkforceMemberIdOrderByEffectiveFromDescIdDesc(
                        tenantId, memberId)
                .stream().map(WorkforceAssignmentResponse::from).collect(Collectors.toList());
    }

    @Transactional
    public WorkforceAssignmentResponse createAssignment(Long tenantId, Long memberId,
                                                          WorkforceAssignmentRequest request) {
        // Match the global lock order used by DriverService: operational Driver,
        // then canonical WorkforceMember. This also serializes reconciliation with
        // concurrent edits/deactivation of an existing projection.
        Driver linkedDriver = drivers.findByWorkforceMemberIdAndTenantIdForUpdate(memberId, tenantId)
                .orElse(null);
        WorkforceMember member = lockMember(tenantId, memberId);
        if (!member.isActive()) {
            throw ApiException.conflict("لا يمكن إنشاء تكليف لشخص غير نشط.");
        }
        if (request.getEffectiveTo() != null
                && request.getEffectiveTo().isBefore(request.getEffectiveFrom())) {
            throw ApiException.badRequest("تاريخ نهاية التكليف يجب ألا يسبق تاريخ بدايته.");
        }

        WorkforceRole role = roles.findByIdAndTenantId(request.getRoleId(), tenantId)
                .orElseThrow(() -> ApiException.notFound("الدور الوظيفي غير موجود."));
        if (!role.isActive()) {
            throw ApiException.conflict("لا يمكن إنشاء تكليف بدور غير نشط.");
        }

        if (request.getSiteId() != null) {
            Site site = sites.findByIdAndTenantId(request.getSiteId(), tenantId)
                    .orElseThrow(() -> ApiException.notFound("الموقع غير موجود."));
            if (!site.isActive()) {
                throw ApiException.conflict("لا يمكن ربط تكليف بموقع غير نشط.");
            }
        }

        if (assignments.existsOverlappingAssignment(tenantId, memberId, role.getId(),
                request.getEffectiveFrom(), request.getEffectiveTo())) {
            throw ApiException.conflict("يوجد تكليف متداخل لنفس الشخص والدور خلال هذه الفترة.");
        }

        WorkforceAssignment assignment = new WorkforceAssignment(
                tenantId, memberId, role.getId(), request.getSiteId(),
                request.getEffectiveFrom(), request.getEffectiveTo(),
                request.getNotes() == null || request.getNotes().trim().isEmpty()
                        ? null : request.getNotes().trim());
        WorkforceAssignment saved = assignments.saveAndFlush(assignment);
        ensureOperationalDriver(member, role, linkedDriver);
        return WorkforceAssignmentResponse.from(saved);
    }

    @Transactional
    public WorkforceAssignmentResponse endAssignment(Long tenantId, Long memberId, Long assignmentId,
                                                       EndWorkforceAssignmentRequest request) {
        lockMember(tenantId, memberId);
        WorkforceAssignment assignment = assignments.findByIdAndTenantIdForUpdate(assignmentId, tenantId)
                .orElseThrow(() -> ApiException.notFound("التكليف الوظيفي غير موجود."));
        if (!memberId.equals(assignment.getWorkforceMemberId())) {
            throw ApiException.notFound("التكليف الوظيفي غير موجود.");
        }
        assignment.requireVersion(request.getVersion());
        if (assignment.getStatus() != WorkforceAssignmentStatus.ACTIVE) {
            throw ApiException.conflict("هذا التكليف ليس نشطًا.");
        }
        if (request.getEffectiveTo().isBefore(assignment.getEffectiveFrom())) {
            throw ApiException.badRequest("تاريخ نهاية التكليف يجب ألا يسبق تاريخ بدايته.");
        }
        assignment.endOn(request.getEffectiveTo());
        return WorkforceAssignmentResponse.from(assignments.saveAndFlush(assignment));
    }


    /**
     * The current shift aggregate still persists its responsible operator through
     * shifts.driver_id. Keep a one-to-one operational projection for roles that
     * can occupy that slot; WorkforceMember remains the only person identity.
     * Zero legacy rates are compatibility placeholders, never contractual wages.
     */
    private void ensureOperationalDriver(WorkforceMember member, WorkforceRole role, Driver operational) {
        if (!role.isShiftEligible()) {
            return;
        }
        WorkerType workerType = "DRIVER".equals(role.getCode())
                || "EQUIPMENT_OPERATOR".equals(role.getCode())
                ? WorkerType.DRIVER : WorkerType.WORKER;

        // Workforce is the canonical identity and active-state source. A linked
        // legacy projection may exist but be inactive or stale (for example after
        // a member was reactivated in Workforce). Reconcile only identity/type and
        // active state; preserve operational availability and all legacy rates.
        if (operational != null) {
            operational.setName(member.getFullName());
            operational.setPhone(member.getPhone());
            operational.setWorkerType(workerType);
            operational.setActive(member.isActive());
            drivers.saveAndFlush(operational);
            return;
        }

        operational = new Driver(member.getTenantId(), member.getFullName(), workerType);
        operational.setWorkforceMemberId(member.getId());
        operational.setPhone(member.getPhone());
        operational.setMorningRate(BigDecimal.ZERO);
        operational.setEveningRate(BigDecimal.ZERO);
        operational.setNightRate(BigDecimal.ZERO);
        operational.setActive(member.isActive());
        drivers.saveAndFlush(operational);
    }

    private WorkforceMember lockMember(Long tenantId, Long memberId) {
        return members.findByIdAndTenantIdForUpdate(memberId, tenantId)
                .orElseThrow(() -> ApiException.notFound("العامل غير موجود."));
    }

    private WorkforceMember requireMember(Long tenantId, Long memberId) {
        return members.findByIdAndTenantId(memberId, tenantId)
                .orElseThrow(() -> ApiException.notFound("العامل غير موجود."));
    }
}
