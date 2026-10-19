package com.contractor.app.driver.api.dto;

import com.contractor.app.driver.domain.Driver;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DriverResponse {

    private final Long id;
    private final String name;
    private final String phone;
    private final String nationalId;
    private final String workerType;
    private final BigDecimal morningRate;
    private final BigDecimal eveningRate;
    private final BigDecimal nightRate;
    private final String notes;
    private final boolean active;
    private final Long version;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private DriverResponse(Driver d) {
        this.id = d.getId();
        this.name = d.getName();
        this.phone = d.getPhone();
        this.nationalId = d.getNationalId();
        this.workerType = d.getWorkerType().name();
        this.morningRate = d.getMorningRate();
        this.eveningRate = d.getEveningRate();
        this.nightRate = d.getNightRate();
        this.notes = d.getNotes();
        this.active = d.isActive();
        this.version = d.getVersion();
        this.createdAt = d.getCreatedAt();
        this.updatedAt = d.getUpdatedAt();
    }

    public static DriverResponse from(Driver driver) {
        return new DriverResponse(driver);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getNationalId() {
        return nationalId;
    }

    public String getWorkerType() {
        return workerType;
    }

    public BigDecimal getMorningRate() {
        return morningRate;
    }

    public BigDecimal getEveningRate() {
        return eveningRate;
    }

    public BigDecimal getNightRate() {
        return nightRate;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
