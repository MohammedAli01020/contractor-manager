package com.contractor.app.equipment.domain;

/** الحالة التشغيلية للمعدة، منفصلة عن "التعطيل" (الأرشفة). */
public enum EquipmentStatus {
    AVAILABLE,
    MAINTENANCE,
    OUT_OF_SERVICE
}
