package com.contractor.app.auth.api.dto;

import com.contractor.app.common.validation.ValidationRules;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class RegisterTenantRequest {

    @NotBlank(message = "اسم المقاول أو الشركة مطلوب")
    @Size(max = 150, message = "اسم المقاول يجب ألا يتجاوز 150 حرفًا")
    private String tenantName;

    @NotBlank(message = "الاسم الكامل مطلوب")
    @Size(max = 150, message = "الاسم الكامل يجب ألا يتجاوز 150 حرفًا")
    private String fullName;

    @NotBlank(message = "اسم المستخدم مطلوب")
    @Pattern(regexp = ValidationRules.USERNAME_REGEX, message = ValidationRules.USERNAME_MESSAGE)
    private String username;

    @NotBlank(message = "كلمة المرور مطلوبة")
    @Pattern(regexp = ValidationRules.PASSWORD_REGEX, message = ValidationRules.PASSWORD_MESSAGE)
    private String password;

    @Size(max = 30, message = "رقم الهاتف يجب ألا يتجاوز 30 حرفًا")
    private String phone;

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
