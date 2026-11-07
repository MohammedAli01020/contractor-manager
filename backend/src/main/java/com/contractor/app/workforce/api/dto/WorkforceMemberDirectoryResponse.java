package com.contractor.app.workforce.api.dto;

import com.contractor.app.driver.domain.WorkerType;
import com.contractor.app.workforce.infra.WorkforceMemberDirectoryRow;

public class WorkforceMemberDirectoryResponse {
    private final Long workforceMemberId;
    private final Long driverId;
    private final String fullName;
    private final String phone;
    private final boolean active;
    private final String workerType;
    private final Boolean driverActive;
    private final boolean operationalProfileLinked;

    private WorkforceMemberDirectoryResponse(WorkforceMemberDirectoryRow row) {
        this.workforceMemberId = row.getWorkforceMemberId();
        this.driverId = row.getDriverId();
        this.fullName = row.getFullName();
        this.phone = row.getPhone();
        this.active = row.getActive();
        WorkerType type = row.getWorkerType();
        this.workerType = type == null ? null : type.name();
        this.driverActive = row.getDriverActive();
        this.operationalProfileLinked = row.getDriverId() != null;
    }

    public static WorkforceMemberDirectoryResponse from(WorkforceMemberDirectoryRow row) {
        return new WorkforceMemberDirectoryResponse(row);
    }

    public Long getWorkforceMemberId() { return workforceMemberId; }
    public Long getDriverId() { return driverId; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public boolean isActive() { return active; }
    public String getWorkerType() { return workerType; }
    public Boolean getDriverActive() { return driverActive; }
    public boolean isOperationalProfileLinked() { return operationalProfileLinked; }
}
