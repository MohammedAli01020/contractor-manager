package com.contractor.app.workforce.domain;

import com.contractor.app.common.error.ApiException;
import com.contractor.app.site.domain.Site;
import com.contractor.app.site.infra.SiteRepository;
import com.contractor.app.tenant.infra.TenantRepository;
import com.contractor.app.workforce.api.dto.EndWorkforceAssignmentRequest;
import com.contractor.app.workforce.api.dto.WorkforceAssignmentRequest;
import com.contractor.app.workforce.api.dto.WorkforceAssignmentResponse;
import com.contractor.app.workforce.api.dto.WorkforceRoleRequest;
import com.contractor.app.workforce.api.dto.WorkforceRoleResponse;
import com.contractor.app.workforce.infra.WorkforceAssignmentRepository;
import com.contractor.app.workforce.infra.WorkforceMemberRepository;
import com.contractor.app.workforce.infra.WorkforceRoleRepository;
import java.time.LocalDate;
import java.util.List;
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

    public WorkforceAssignmentService(WorkforceMemberRepository members,
                                      WorkforceRoleRepository roles,
                                      WorkforceAssignmentRepository assignments,
                                      SiteRepository sites,
                                      TenantRepository tenants) {
        this.members = members;
        this.roles = roles;
        this.assignments = assignments;
        this.sites = sites;
        this.tenants = tenants;
    }

    @Transactional(readOnly = true)
    public List<WorkforceRoleResponse> listRoles(Long tenantId, boolean includeInactive) {
        return roles.findAllByTenantIdOrderByNameAsc(tenantId).stream()
                .filter(role -> includeInactive || role.isActive())
                .map(WorkforceRoleResponse::from).collect(Collectors.toList());
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
        return WorkforceRoleResponse.from(roles.saveAndFlush(role));
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
        return WorkforceAssignmentResponse.from(assignments.saveAndFlush(assignment));
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

    private WorkforceMember lockMember(Long tenantId, Long memberId) {
        return members.findByIdAndTenantIdForUpdate(memberId, tenantId)
                .orElseThrow(() -> ApiException.notFound("العامل غير موجود."));
    }

    private WorkforceMember requireMember(Long tenantId, Long memberId) {
        return members.findByIdAndTenantId(memberId, tenantId)
                .orElseThrow(() -> ApiException.notFound("العامل غير موجود."));
    }
}
