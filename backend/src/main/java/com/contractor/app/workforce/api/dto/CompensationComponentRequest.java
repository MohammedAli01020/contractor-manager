package com.contractor.app.workforce.api.dto;

import com.contractor.app.workforce.domain.CompensationBasis;
import com.contractor.app.workforce.domain.CompensationShiftType;
import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class CompensationComponentRequest {
    @NotBlank @Size(max = 60)
    private String componentCode;
    @NotBlank @Size(max = 120)
    private String displayName;
    @NotNull
    private CompensationBasis basis;
    @NotNull @DecimalMin("0.00")
    private BigDecimal rate;
    @Size(max = 30)
    private String unitCode;
    private CompensationShiftType shiftType;
    private Integer sortOrder;

    public String getComponentCode() { return componentCode; }
    public void setComponentCode(String value) { componentCode = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { displayName = value; }
    public CompensationBasis getBasis() { return basis; }
    public void setBasis(CompensationBasis value) { basis = value; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal value) { rate = value; }
    public String getUnitCode() { return unitCode; }
    public void setUnitCode(String value) { unitCode = value; }
    public CompensationShiftType getShiftType() { return shiftType; }
    public void setShiftType(CompensationShiftType value) { shiftType = value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { sortOrder = value; }
}
