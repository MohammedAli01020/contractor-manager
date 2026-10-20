package com.contractor.app.live.api.dto;

import java.time.LocalDate;

public class LiveDriverResponse {

    private final Long id;
    private final String name;
    private final String workerType;
    private final String availability;
    private final String unavailableReason;
    private final String liveStatus;
    private final Long currentShiftId;
    private final LocalDate runningShiftDate;
    private final String siteName;
    private final String equipmentCode;
    private final int runningShiftsCount;

    public LiveDriverResponse(Long id, String name, String workerType, String availability,
                              String unavailableReason, String liveStatus, Long currentShiftId,
                              LocalDate runningShiftDate, String siteName, String equipmentCode,
                              int runningShiftsCount) {
        this.id = id;
        this.name = name;
        this.workerType = workerType;
        this.availability = availability;
        this.unavailableReason = unavailableReason;
        this.liveStatus = liveStatus;
        this.currentShiftId = currentShiftId;
        this.runningShiftDate = runningShiftDate;
        this.siteName = siteName;
        this.equipmentCode = equipmentCode;
        this.runningShiftsCount = runningShiftsCount;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getWorkerType() {
        return workerType;
    }

    public String getAvailability() {
        return availability;
    }

    public String getUnavailableReason() {
        return unavailableReason;
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

    public String getEquipmentCode() {
        return equipmentCode;
    }

    public int getRunningShiftsCount() {
        return runningShiftsCount;
    }
}
