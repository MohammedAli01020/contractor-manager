-- المواقع
CREATE TABLE sites (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id  BIGINT       NOT NULL,
    name       VARCHAR(150) NOT NULL,
    address    VARCHAR(300) NULL,
    notes      VARCHAR(500) NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_sites_tenant_name (tenant_id, name),
    UNIQUE KEY uq_sites_id_tenant (id, tenant_id),
    CONSTRAINT fk_sites_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- السائقون والعمال: أجر كل نوع وردية
CREATE TABLE drivers (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT        NOT NULL,
    name         VARCHAR(150)  NOT NULL,
    phone        VARCHAR(30)   NULL,
    national_id  VARCHAR(30)   NULL,
    worker_type  VARCHAR(20)   NOT NULL DEFAULT 'DRIVER',
    morning_rate DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    evening_rate DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    night_rate   DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    notes        VARCHAR(500)  NULL,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_drivers_id_tenant (id, tenant_id),
    KEY ix_drivers_tenant_name (tenant_id, name),
    CONSTRAINT fk_drivers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_drivers_rates CHECK (morning_rate >= 0 AND evening_rate >= 0 AND night_rate >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- أنواع المعدات (يضيف المقاول ما يشاء)
CREATE TABLE equipment_types (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id  BIGINT       NOT NULL,
    name       VARCHAR(100) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_equipment_types_tenant_name (tenant_id, name),
    UNIQUE KEY uq_equipment_types_id_tenant (id, tenant_id),
    CONSTRAINT fk_equipment_types_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- المعدات
CREATE TABLE equipment (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id               BIGINT        NOT NULL,
    code                    VARCHAR(50)   NOT NULL,
    name                    VARCHAR(150)  NULL,
    equipment_type_id       BIGINT        NOT NULL,
    operating_cost_per_shift DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',
    notes                   VARCHAR(500)  NULL,
    active                  BOOLEAN       NOT NULL DEFAULT TRUE,
    version                 BIGINT        NOT NULL DEFAULT 0,
    created_at              DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at              DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_equipment_tenant_code (tenant_id, code),
    UNIQUE KEY uq_equipment_id_tenant (id, tenant_id),
    KEY ix_equipment_type_tenant (equipment_type_id, tenant_id),
    CONSTRAINT fk_equipment_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    -- مفتاح مركّب: يستحيل ربط معدة بنوع يخص مقاولًا آخر
    CONSTRAINT fk_equipment_type_tenant FOREIGN KEY (equipment_type_id, tenant_id)
        REFERENCES equipment_types (id, tenant_id),
    CONSTRAINT ck_equipment_cost CHECK (operating_cost_per_shift >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
