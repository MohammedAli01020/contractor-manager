package com.contractor.app.workforce.domain;

import com.contractor.app.workforce.api.dto.WorkforceAssignmentResponse;
import com.contractor.app.workforce.api.dto.WorkforceMemberResponse;
import com.contractor.app.workforce.api.dto.WorkforceOnboardRequest;
import com.contractor.app.workforce.api.dto.WorkforceOnboardResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates canonical identity creation and initial role assignments in one
 * transaction. Any invalid assignment rolls back the identity and its audit row.
 */
@Service
public class WorkforceOnboardingService {

    private final WorkforceIdentityService identities;
    private final WorkforceAssignmentService assignments;

    public WorkforceOnboardingService(WorkforceIdentityService identities,
                                      WorkforceAssignmentService assignments) {
        this.identities = identities;
        this.assignments = assignments;
    }

    @Transactional
    public WorkforceOnboardResponse onboard(Long tenantId, WorkforceOnboardRequest request) {
        if (request.getInitialAssignments() == null || request.getInitialAssignments().isEmpty()) {
            throw com.contractor.app.common.error.ApiException.badRequest(
                    "اختر دورًا وظيفيًا واحدًا على الأقل.");
        }

        Set<Long> roleIds = new HashSet<>();
        for (com.contractor.app.workforce.api.dto.WorkforceAssignmentRequest assignment
                : request.getInitialAssignments()) {
            if (assignment == null || assignment.getRoleId() == null
                    || !roleIds.add(assignment.getRoleId())) {
                throw com.contractor.app.common.error.ApiException.badRequest(
                        "كل دور وظيفي يجب أن يكون محددًا مرة واحدة فقط.");
            }
        }

        WorkforceMember member = identities.createMember(tenantId, request.getMember());
        List<WorkforceAssignmentResponse> created = new ArrayList<>();
        for (com.contractor.app.workforce.api.dto.WorkforceAssignmentRequest assignment
                : request.getInitialAssignments()) {
            created.add(assignments.createAssignment(tenantId, member.getId(), assignment));
        }
        return new WorkforceOnboardResponse(WorkforceMemberResponse.from(member, true), created);
    }
}
