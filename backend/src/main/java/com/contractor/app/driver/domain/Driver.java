package com.contractor.app.driver.domain;

import com.contractor.app.common.domain.BaseTenantEntity;
import java.math.BigDecimal;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

@Entity
@Table(name = "drivers")
public class Driver extends BaseTenantEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(name = "national_id", length = 30)
    private String nationalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "worker_type", nullable = false, length = 20)
    private WorkerType workerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DriverAvailability availability = DriverAvailability.AVAILABLE;

    @Column(name = "unavailable_reason", length = 200)
    private String unavailableReason;

    @Column(name = "morning_rate", nullable = false, precision = 15, scale = 2)
    private BigDecimal morningRate;

    @Column(name = "evening_rate", nullable = false, precision = 15, scale = 2)
    private BigDecimal eveningRate;

    @Column(name = "night_rate", nullable = false, precision = 15, scale = 2)
    private BigDecimal nightRate;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    protected Driver() {
    }

    public Driver(Long tenantId, String name, WorkerType workerType) {
        super(tenantId);
        this.name = name;
        this.workerType = workerType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNationalId() {
        return nationalId;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public WorkerType getWorkerType() {
        return workerType;
    }

    public void setWorkerType(WorkerType workerType) {
        this.workerType = workerType;
    }

    public BigDecimal getMorningRate() {
        return morningRate;
    }

    public void setMorningRate(BigDecimal morningRate) {
        this.morningRate = morningRate;
    }

    public BigDecimal getEveningRate() {
        return eveningRate;
    }

    public void setEveningRate(BigDecimal eveningRate) {
        this.eveningRate = eveningRate;
    }

    public BigDecimal getNightRate() {
        return nightRate;
    }

    public void setNightRate(BigDecimal nightRate) {
        this.nightRate = nightRate;
    }

    public DriverAvailability getAvailability() {
        return availability;
    }

    public void setAvailability(DriverAvailability availability) {
        this.availability = availability;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }

    public void setUnavailableReason(String unavailableReason) {
        this.unavailableReason = unavailableReason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
