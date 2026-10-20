-- 1) الورديات التجريبية القديمة تُمسح (بموافقتك): النموذج الجديد مختلف ولا فائدة من ترحيلها
DELETE FROM shift_audit_log;
DELETE FROM shifts;
DROP TABLE shift_audit_log;

-- 2) تبسيط الورديات: لا ساعات ولا كمية ولا منع تكرار
ALTER TABLE shifts DROP CHECK ck_shifts_quantity;
ALTER TABLE shifts DROP CHECK ck_shifts_hours;
-- فهرس بديل لمفتاح tenant_id الخارجي قبل حذف الفهارس القديمة
ALTER TABLE shifts ADD KEY ix_shifts_tenant (tenant_id);
ALTER TABLE shifts DROP INDEX uq_shifts_equipment_slot, DROP INDEX uq_shifts_driver_slot;
ALTER TABLE shifts
    DROP COLUMN live_flag,
    DROP COLUMN hours,
    DROP COLUMN quantity,
    DROP COLUMN quantity_unit;

-- وقت الضغط على "إنهاء" (تسجّله الواجهة الخلفية تلقائيًا). وجوده يعني أن الوردية انتهت فعلًا قبل أي إلغاء
ALTER TABLE shifts ADD COLUMN finished_at DATETIME(6) NULL AFTER status;
ALTER TABLE shifts ADD CONSTRAINT ck_shifts_finished CHECK (status <> 'COMPLETED' OR finished_at IS NOT NULL);
ALTER TABLE shifts ADD KEY ix_shifts_tenant_status (tenant_id, status);

-- 3) حالة السائق (متاح / غير متاح) مع سبب نصي
ALTER TABLE drivers
    ADD COLUMN availability VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' AFTER worker_type,
    ADD COLUMN unavailable_reason VARCHAR(200) NULL AFTER availability;

-- 4) سجل تدقيق عام للبرنامج كله (للإضافة فقط)
CREATE TABLE audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT       NOT NULL,
    entity_type VARCHAR(20)  NOT NULL,
    entity_id   BIGINT       NOT NULL,
    action      VARCHAR(20)  NOT NULL,
    field_name  VARCHAR(40)  NULL,
    old_value   VARCHAR(500) NULL,
    new_value   VARCHAR(500) NULL,
    user_id     BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY ix_audit_entity (tenant_id, entity_type, entity_id, created_at),
    KEY ix_audit_time (tenant_id, created_at),
    KEY ix_audit_user (user_id, tenant_id),
    CONSTRAINT fk_audit_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id, tenant_id) REFERENCES users (id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
