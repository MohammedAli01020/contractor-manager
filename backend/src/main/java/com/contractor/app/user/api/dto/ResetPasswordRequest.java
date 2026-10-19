package com.contractor.app.user.api.dto;

import com.contractor.app.common.validation.ValidationRules;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class ResetPasswordRequest {

    @NotBlank(message = "كلمة المرور الجديدة مطلوبة")
    @Pattern(regexp = ValidationRules.PASSWORD_REGEX, message = ValidationRules.PASSWORD_MESSAGE)
    private String newPassword;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
