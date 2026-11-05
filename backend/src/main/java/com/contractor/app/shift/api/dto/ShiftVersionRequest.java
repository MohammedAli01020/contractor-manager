package com.contractor.app.shift.api.dto;

import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;

public class ShiftVersionRequest {

    @NotNull(message = "رقم النسخة (version) مطلوب")
    private Long version;

    /** Required only for HOUR compensation; measured explicitly at finish. */
    @DecimalMin(value = "0.01", message = "عدد الساعات يجب أن يكون أكبر من صفر")
    @Digits(integer = 6, fraction = 2, message = "عدد الساعات يقبل حتى خانتين عشريتين")
    private BigDecimal actualHours;

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public BigDecimal getActualHours() { return actualHours; }

    public void setActualHours(BigDecimal actualHours) { this.actualHours = actualHours; }

    /** Required only for QUANTITY compensation; measured explicitly at finish. */
    @DecimalMin(value = "0.001", message = "الكمية الفعلية يجب أن تكون أكبر من صفر")
    @Digits(integer = 9, fraction = 3, message = "الكمية تقبل حتى ثلاث خانات عشرية")
    private BigDecimal actualQuantity;

    public BigDecimal getActualQuantity() { return actualQuantity; }

    public void setActualQuantity(BigDecimal actualQuantity) { this.actualQuantity = actualQuantity; }
}
