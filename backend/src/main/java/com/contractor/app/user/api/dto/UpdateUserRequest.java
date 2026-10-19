package com.contractor.app.user.api.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class UpdateUserRequest {

    @NotBlank(message = "الاسم الكامل مطلوب")
    @Size(max = 150, message = "الاسم الكامل يجب ألا يتجاوز 150 حرفًا")
    private String fullName;

    @Size(max = 30, message = "رقم الهاتف يجب ألا يتجاوز 30 حرفًا")
    private String phone;

    @NotNull(message = "حالة الحساب مطلوبة")
    private Boolean active;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
