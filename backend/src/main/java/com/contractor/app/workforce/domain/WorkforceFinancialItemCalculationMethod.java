package com.contractor.app.workforce.domain;

/** Bounded calculation methods; tenant input is never executable code or a formula. */
public enum WorkforceFinancialItemCalculationMethod {
    FIXED,
    PER_HOUR,
    PER_DAY,
    PER_SHIFT,
    PER_QUANTITY,
    MANUAL
}
