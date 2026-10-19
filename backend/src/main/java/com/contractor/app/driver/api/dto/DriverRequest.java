package com.contractor.app.driver.api.dto;

import com.contractor.app.driver.domain.WorkerType;
import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class DriverRequest {

    @NotBlank(message = "الاسم مطلوب")
    @Size(max = 150, message = "الاسم يجب ألا يتجاوز 150 حرفًا")
    private String name;

    @Size(max = 30, message = "رقم الهاتف يجب ألا يتجاوز 30 حرفًا")
    private String phone;

    @Size(max = 30, message = "الرقم القومي يجب ألا يتجاوز 30 حرفًا")
    private String nationalId;

    @NotNull(message = "النوع مطلوب (DRIVER أو WORKER)")
    private WorkerType workerType;

    @NotNull(message = "أجر الوردية الصباحية مطلوب")
    @DecimalMin(value = "0", message = "الأجر لا يمكن أن يكون سالبًا")
    @Digits(integer = 13, fraction = 2, message = "الأجر: حتى خانتين عشريتين")
    private BigDecimal morningRate;

    @NotNull(message = "أجر الوردية المسائية مطلوب")
    @DecimalMin(value = "0", message = "الأجر لا يمكن أن يكون سالبًا")
    @Digits(integer = 13, fraction = 2, message = "الأجر: حتى خانتين عشريتين")
    private BigDecimal eveningRate;

    @NotNull(message = "أجر الوردية الليلية مطلوب")
    @DecimalMin(value = "0", message = "الأجر لا يمكن أن يكون سالبًا")
    @Digits(integer = 13, fraction = 2, message = "الأجر: حتى خانتين عشريتين")
    private BigDecimal nightRate;

    @Size(max = 500, message = "الملاحظات يجب ألا تتجاوز 500 حرف")
    private String notes;

    private Boolean active;

    private Long version;

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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
