package com.contractor.app.shift.api.dto;

import java.math.BigDecimal;

/** القيم المقترحة لتعبئة نموذج الوردية. */
public class ShiftDefaultsResponse {

    private final BigDecimal driverWage;
    private final String wageSource;
    private final String compensationBasis;
    private final BigDecimal compensationRate;
    private final String compensationUnitCode;
    private final BigDecimal price;
    private final BigDecimal equipmentOperatingCost;

    public ShiftDefaultsResponse(BigDecimal driverWage, String wageSource, String compensationBasis,
                                 BigDecimal compensationRate, String compensationUnitCode,
                                 BigDecimal price, BigDecimal equipmentOperatingCost) {
        this.driverWage = driverWage;
        this.wageSource = wageSource;
        this.compensationBasis = compensationBasis;
        this.compensationRate = compensationRate;
        this.compensationUnitCode = compensationUnitCode;
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

    public String getCompensationUnitCode() { return compensationUnitCode; }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getEquipmentOperatingCost() {
        return equipmentOperatingCost;
    }
}
