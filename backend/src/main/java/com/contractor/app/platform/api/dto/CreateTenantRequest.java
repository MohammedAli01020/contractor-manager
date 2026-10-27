package com.contractor.app.platform.api.dto;

import com.contractor.app.common.validation.ValidationRules;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class CreateTenantRequest {
    @NotBlank @Size(max=150) private String tenantName;
    @NotBlank @Size(max=150) private String adminFullName;
    @NotBlank @Pattern(regexp=ValidationRules.USERNAME_REGEX, message=ValidationRules.USERNAME_MESSAGE) private String adminUsername;
    @Size(max=30) private String adminPhone;
    @NotBlank @Email @Size(max=254) private String adminEmail;
    public String getTenantName(){return tenantName;}
    public void setTenantName(String value){tenantName=value;}
    public String getAdminFullName(){return adminFullName;}
    public void setAdminFullName(String value){adminFullName=value;}
    public String getAdminUsername(){return adminUsername;}
    public void setAdminUsername(String value){adminUsername=value;}
    public String getAdminPhone(){return adminPhone;}
    public void setAdminPhone(String value){adminPhone=value;}
    public String getAdminEmail(){return adminEmail;}
    public void setAdminEmail(String value){adminEmail=value;}
}
