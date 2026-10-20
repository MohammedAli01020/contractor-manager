package com.contractor.app.live.api.dto;

import java.time.LocalDate;

public class LiveEquipmentResponse {

    private final Long id;
    private final String code;
    private final String name;
    private final String equipmentTypeName;
    private final String status;
    private final String liveStatus;
    private final Long currentShiftId;
    private final LocalDate runningShiftDate;
    private final String siteName;
    private final String driverName;
    private final int runningShiftsCount;

    public LiveEquipmentResponse(Long id, String code, String name, String equipmentTypeName, String status,
                                 String liveStatus, Long currentShiftId, LocalDate runningShiftDate,
                                 String siteName, String driverName, int runningShiftsCount) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.equipmentTypeName = equipmentTypeName;
        this.status = status;
        this.liveStatus = liveStatus;
        this.currentShiftId = currentShiftId;
        this.runningShiftDate = runningShiftDate;
        this.siteName = siteName;
        this.driverName = driverName;
        this.runningShiftsCount = runningShiftsCount;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getEquipmentTypeName() {
        return equipmentTypeName;
    }

    public String getStatus() {
        return status;
    }

    public String getLiveStatus() {
        return liveStatus;
    }

    public Long getCurrentShiftId() {
        return currentShiftId;
    }

    public LocalDate getRunningShiftDate() {
        return runningShiftDate;
    }

    public String getSiteName() {
        return siteName;
    }

    public String getDriverName() {
        return driverName;
    }

    public int getRunningShiftsCount() {
        return runningShiftsCount;
    }
}
