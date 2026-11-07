package com.contractor.app.workforce.infra;

import com.contractor.app.driver.domain.WorkerType;

/** Read-only projection joining canonical workforce identity to its optional legacy driver. */
public interface WorkforceMemberDirectoryRow {
    Long getWorkforceMemberId();
    Long getDriverId();
    String getFullName();
    String getPhone();
    boolean getActive();
    WorkerType getWorkerType();
    Boolean getDriverActive();
}
