package com.contractor.app.site.api.dto;

import com.contractor.app.site.domain.Site;
import java.time.LocalDateTime;

public class SiteResponse {

    private final Long id;
    private final String name;
    private final String address;
    private final String notes;
    private final boolean active;
    private final Long version;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private SiteResponse(Site s) {
        this.id = s.getId();
        this.name = s.getName();
        this.address = s.getAddress();
        this.notes = s.getNotes();
        this.active = s.isActive();
        this.version = s.getVersion();
        this.createdAt = s.getCreatedAt();
        this.updatedAt = s.getUpdatedAt();
    }

    public static SiteResponse from(Site site) {
        return new SiteResponse(site);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isActive() {
        return active;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
