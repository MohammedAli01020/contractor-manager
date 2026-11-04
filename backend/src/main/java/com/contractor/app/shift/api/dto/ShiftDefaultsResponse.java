package com.contractor.app.shift.api.dto;

import java.math.BigDecimal;

/** القيم المقترحة لتعبئة نموذج الوردية. */
public class ShiftDefaultsResponse {

    private final BigDecimal driverWage;
    private final String wageSource;
    private final String compensationBasis;
    private final BigDecimal compensationRate;
    private final BigDecimal price;
    private final BigDecimal equipmentOperatingCost;

    public ShiftDefaultsResponse(BigDecimal driverWage, String wageSource, String compensationBasis,
                                 BigDecimal compensationRate, BigDecimal price, BigDecimal equipmentOperatingCost) {
        this.driverWage = driverWage;
        this.wageSource = wageSource;
        this.compensationBasis = compensationBasis;
        this.compensationRate = compensationRate;
        this.price = price;
        this.equipmentOperatingCost = equipmentOperatingCost;
    }

    public BigDecimal getDriverWage() {
        return driverWage;
    }

    public String getWageSource() {
        return wageSource;
    }

    public String getCompensationBasis() { return compensationBasis; }

    public BigDecimal getCompensationRate() { return compensationRate; }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getEquipmentOperatingCost() {
        return equipmentOperatingCost;
    }
}
