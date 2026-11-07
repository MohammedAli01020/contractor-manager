package com.contractor.app.workforce.api.dto;

import java.util.List;

/** Result of the atomic person-and-initial-assignments onboarding operation. */
public class WorkforceOnboardResponse {
    private final WorkforceMemberResponse member;
    private final List<WorkforceAssignmentResponse> assignments;

    public WorkforceOnboardResponse(WorkforceMemberResponse member,
                                    List<WorkforceAssignmentResponse> assignments) {
        this.member = member;
        this.assignments = assignments;
    }

    public WorkforceMemberResponse getMember() { return member; }
    public List<WorkforceAssignmentResponse> getAssignments() { return assignments; }
}
