package com.contractor.app.site.api.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class SiteRequest {

    @NotBlank(message = "اسم الموقع مطلوب")
    @Size(max = 150, message = "اسم الموقع يجب ألا يتجاوز 150 حرفًا")
    private String name;

    @Size(max = 300, message = "العنوان يجب ألا يتجاوز 300 حرف")
    private String address;

    @Size(max = 500, message = "الملاحظات يجب ألا تتجاوز 500 حرف")
    private String notes;

    /** اختياري: الافتراضي فعّال عند الإنشاء، ويُترك كما هو عند التعديل إن لم يُرسل. */
    private Boolean active;

    /** مطلوب عند التعديل فقط. */
    private Long version;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
