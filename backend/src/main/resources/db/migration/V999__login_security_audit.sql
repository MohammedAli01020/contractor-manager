ALTER TABLE users
    ADD COLUMN last_login_at DATETIME NULL;

ALTER TABLE platform_owners
    ADD COLUMN last_login_at DATETIME NULL;

CREATE TABLE login_security_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    principal_type VARCHAR(30) NOT NULL,
    principal_id BIGINT NULL,
    tenant_id BIGINT NULL,
    username VARCHAR(60) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    reason VARCHAR(50) NULL,
    client_ip VARCHAR(45) NULL,
    request_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_login_event_type_created (principal_type, created_at, id),
    INDEX idx_login_event_tenant_created (tenant_id, created_at, id),
    INDEX idx_login_event_username_created (username, created_at)
) ENGINE=InnoDB;
