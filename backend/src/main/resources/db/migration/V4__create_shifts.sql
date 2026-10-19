-- سعر الوردية الافتراضي للمعدة (يُقترح تلقائيًا عند تسجيل وردية ويمكن تعديله فيها)
ALTER TABLE equipment
    ADD COLUMN default_shift_price DECIMAL(15,2) NOT NULL DEFAULT 0.00 AFTER operating_cost_per_shift,
    ADD CONSTRAINT ck_equipment_default_price CHECK (default_shift_price >= 0);

-- الورديات. price وdriver_wage وequipment_operating_cost "نسخة ثابتة" وقت التسجيل.
CREATE TABLE shifts (
    id                       BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id                BIGINT        NOT NULL,
    shift_date               DATE          NOT NULL,
    shift_type               VARCHAR(20)   NOT NULL,
    status                   VARCHAR(20)   NOT NULL DEFAULT 'COMPLETED',
    site_id                  BIGINT        NOT NULL,
    driver_id                BIGINT        NOT NULL,
    equipment_id             BIGINT        NOT NULL,
    hours                    DECIMAL(5,2)  NULL,
    quantity                 DECIMAL(12,2) NULL,
    quantity_unit            VARCHAR(30)   NULL,
    price                    DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    driver_wage              DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    equipment_operating_cost DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    notes                    VARCHAR(500)  NULL,
    cancel_reason            VARCHAR(300)  NULL,
    -- 1 للورديات المكتملة وNULL للملغاة: يسمح فهرس UNIQUE بتكرار الملغاة ويمنع تكرار المكتملة
    live_flag                TINYINT       AS (CASE WHEN status = 'COMPLETED' THEN 1 ELSE NULL END) STORED,
    version                  BIGINT        NOT NULL DEFAULT 0,
    created_at               DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at               DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_shifts_id_tenant (id, tenant_id),
    -- المعدة الواحدة لا تعمل في نفس الوردية والتاريخ مرتين، وكذلك السائق
    UNIQUE KEY uq_shifts_equipment_slot (tenant_id, equipment_id, shift_date, shift_type, live_flag),
    UNIQUE KEY uq_shifts_driver_slot (tenant_id, driver_id, shift_date, shift_type, live_flag),
    KEY ix_shifts_tenant_date (tenant_id, shift_date),
    KEY ix_shifts_site (site_id, tenant_id),
    KEY ix_shifts_driver (driver_id, tenant_id),
    KEY ix_shifts_equipment (equipment_id, tenant_id),
    CONSTRAINT fk_shifts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_shifts_site FOREIGN KEY (site_id, tenant_id) REFERENCES sites (id, tenant_id),
    CONSTRAINT fk_shifts_driver FOREIGN KEY (driver_id, tenant_id) REFERENCES drivers (id, tenant_id),
    CONSTRAINT fk_shifts_equipment FOREIGN KEY (equipment_id, tenant_id) REFERENCES equipment (id, tenant_id),
    CONSTRAINT ck_shifts_amounts CHECK (price >= 0 AND driver_wage >= 0 AND equipment_operating_cost >= 0),
    CONSTRAINT ck_shifts_hours CHECK (hours IS NULL OR (hours >= 0 AND hours <= 24)),
    CONSTRAINT ck_shifts_quantity CHECK (quantity IS NULL OR quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- سجل تعديلات الورديات: يُضاف إليه فقط ولا يُعدَّل ولا يُحذف
CREATE TABLE shift_audit_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id  BIGINT       NOT NULL,
    shift_id   BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    action     VARCHAR(20)  NOT NULL,
    field_name VARCHAR(40)  NULL,
    old_value  VARCHAR(500) NULL,
    new_value  VARCHAR(500) NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY ix_shift_audit_shift (shift_id, tenant_id, created_at),
    KEY ix_shift_audit_user (user_id, tenant_id),
    CONSTRAINT fk_shift_audit_shift FOREIGN KEY (shift_id, tenant_id) REFERENCES shifts (id, tenant_id),
    CONSTRAINT fk_shift_audit_user FOREIGN KEY (user_id, tenant_id) REFERENCES users (id, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
