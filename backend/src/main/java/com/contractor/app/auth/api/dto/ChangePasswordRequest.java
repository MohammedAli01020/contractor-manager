package com.contractor.app.auth.api.dto;

import com.contractor.app.common.validation.ValidationRules;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class ChangePasswordRequest {

    @NotBlank(message = "كلمة المرور الحالية مطلوبة")
    private String currentPassword;

    @NotBlank(message = "كلمة المرور الجديدة مطلوبة")
    @Pattern(regexp = ValidationRules.PASSWORD_REGEX, message = ValidationRules.PASSWORD_MESSAGE)
    private String newPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
