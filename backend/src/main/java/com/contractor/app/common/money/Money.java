package com.contractor.app.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    private Money() {
    }

    /** كل المبالغ تُخزَّن وتُعرض بخانتين عشريتين. */
    public static BigDecimal scale(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
