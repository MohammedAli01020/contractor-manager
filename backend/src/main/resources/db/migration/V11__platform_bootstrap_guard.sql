CREATE TABLE platform_bootstrap_guard (
 id BIGINT NOT NULL PRIMARY KEY,
 version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
INSERT INTO platform_bootstrap_guard (id, version) VALUES (1, 0);
