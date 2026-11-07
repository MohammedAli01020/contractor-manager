package com.contractor.app.workforce.api.dto;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/** Creates one canonical identity together with its required initial role assignments. */
public class WorkforceOnboardRequest {

    @Valid
    @NotNull
    private WorkforceMemberRequest member;

    @Valid
    @NotEmpty(message = "اختر دورًا وظيفيًا واحدًا على الأقل.")
    private List<@NotNull WorkforceAssignmentRequest> initialAssignments = new ArrayList<>();

    public WorkforceMemberRequest getMember() { return member; }
    public void setMember(WorkforceMemberRequest member) { this.member = member; }
    public List<WorkforceAssignmentRequest> getInitialAssignments() { return initialAssignments; }
    public void setInitialAssignments(List<WorkforceAssignmentRequest> initialAssignments) {
        this.initialAssignments = initialAssignments;
    }
}
