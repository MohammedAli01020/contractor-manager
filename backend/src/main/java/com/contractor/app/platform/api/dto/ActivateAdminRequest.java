package com.contractor.app.platform.api.dto;
import com.contractor.app.common.validation.ValidationRules;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
public class ActivateAdminRequest {
 @NotBlank private String token;
 @NotBlank @Pattern(regexp=ValidationRules.PASSWORD_REGEX,message=ValidationRules.PASSWORD_MESSAGE) private String password;
 public String getToken(){return token;} public void setToken(String v){token=v;}
 public String getPassword(){return password;} public void setPassword(String v){password=v;}
}