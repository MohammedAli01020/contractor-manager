package com.contractor.app.shift.domain;

/** الوردية تُسجَّل بعد حدوثها، فهي إما مكتملة أو ملغاة. الملغاة لا تدخل في أي حساب. */
public enum ShiftStatus {
    COMPLETED,
    CANCELLED
}
