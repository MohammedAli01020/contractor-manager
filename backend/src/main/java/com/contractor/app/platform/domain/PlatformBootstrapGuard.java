package com.contractor.app.platform.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

@Entity
@Table(name = "platform_bootstrap_guard")
public class PlatformBootstrapGuard {
    @Id private Long id;
    @Version @Column(nullable = false) private Long version;
    protected PlatformBootstrapGuard() { }
    public Long getId() { return id; }
}
