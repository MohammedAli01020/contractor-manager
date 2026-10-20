package com.contractor.app.shift.domain;

/** مؤشر الوردية: شغالة ثم منتهية، أو ملغاة. الملغاة والشغالة لا تدخلان في أي حساب. */
public enum ShiftStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
