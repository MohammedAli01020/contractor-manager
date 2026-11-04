package com.contractor.app.shift.api.dto;

import java.math.BigDecimal;

/** القيم المقترحة لتعبئة نموذج الوردية. */
public class ShiftDefaultsResponse {

    private final BigDecimal driverWage;
    private final String wageSource;
    private final BigDecimal price;
    private final BigDecimal equipmentOperatingCost;

    public ShiftDefaultsResponse(BigDecimal driverWage, String wageSource, BigDecimal price, BigDecimal equipmentOperatingCost) {
        this.driverWage = driverWage;
        this.wageSource = wageSource;
        this.price = price;
        this.equipmentOperatingCost = equipmentOperatingCost;
    }

    public BigDecimal getDriverWage() {
        return driverWage;
    }

    public String getWageSource() {
        return wageSource;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getEquipmentOperatingCost() {
        return equipmentOperatingCost;
    }
}
