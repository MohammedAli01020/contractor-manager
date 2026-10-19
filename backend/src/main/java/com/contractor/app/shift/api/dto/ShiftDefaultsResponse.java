package com.contractor.app.shift.api.dto;

import java.math.BigDecimal;

/** القيم المقترحة لتعبئة نموذج الوردية. */
public class ShiftDefaultsResponse {

    private final BigDecimal driverWage;
    private final BigDecimal price;
    private final BigDecimal equipmentOperatingCost;

    public ShiftDefaultsResponse(BigDecimal driverWage, BigDecimal price, BigDecimal equipmentOperatingCost) {
        this.driverWage = driverWage;
        this.price = price;
        this.equipmentOperatingCost = equipmentOperatingCost;
    }

    public BigDecimal getDriverWage() {
        return driverWage;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getEquipmentOperatingCost() {
        return equipmentOperatingCost;
    }
}
