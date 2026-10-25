package com.contractor.app.user.api.dto;

import com.contractor.app.common.validation.ValidationRules;
import com.contractor.app.user.domain.Role;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class CreateUserRequest {

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

    // Defaults to OPERATOR in the service when omitted; ADMIN cannot be granted through this endpoint.
    private Role role;

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

    public Role getRole() { return role; }

    public void setRole(Role role) { this.role = role; }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
