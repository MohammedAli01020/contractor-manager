package com.contractor.app.workforce.api.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** Common identity fields only; category-specific operational data is managed separately. */
public class WorkforceMemberRequest {

    @NotBlank(message = "اسم الشخص مطلوب")
    @Size(max = 150, message = "الاسم يجب ألا يتجاوز 150 حرفًا")
    private String fullName;

    @Size(max = 30, message = "رقم الهاتف يجب ألا يتجاوز 30 حرفًا")
    private String phone;

    @Size(max = 30, message = "الرقم القومي يجب ألا يتجاوز 30 حرفًا")
    private String nationalId;

    @Size(max = 500, message = "الملاحظات يجب ألا تتجاوز 500 حرف")
    private String notes;

    private Long version;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
