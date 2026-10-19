package com.contractor.app.common.domain;

import com.contractor.app.common.error.ApiException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Version;

/** أساس كل كيان يخص مقاولًا: المعرّف والمقاول ونسخة التعديل وأوقات الإنشاء والتعديل. */
@MappedSuperclass
public abstract class BaseTenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected BaseTenantEntity() {
    }

    protected BaseTenantEntity(Long tenantId) {
        this.tenantId = tenantId;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    /** يمنع الكتابة فوق تعديل مستخدم آخر: العميل يرسل النسخة التي رآها. */
    public void requireVersion(Long expected) {
        if (expected == null) {
            throw ApiException.badRequest("رقم النسخة (version) مطلوب عند التعديل.");
        }
        if (!expected.equals(version)) {
            throw ApiException.conflict("تم تعديل هذا السجل بواسطة مستخدم آخر. أعد التحميل ثم حاول مجددًا.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
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
