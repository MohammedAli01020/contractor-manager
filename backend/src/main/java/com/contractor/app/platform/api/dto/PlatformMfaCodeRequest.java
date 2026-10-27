package com.contractor.app.platform.api.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class PlatformMfaCodeRequest {
    @NotBlank private String challengeToken;
    @Pattern(regexp = "^$|\\d{6}") private String code = "";
    @Pattern(regexp = "^$|[A-Za-z0-9-]{8,40}") private String recoveryCode = "";
    public String getChallengeToken() { return challengeToken; }
    public void setChallengeToken(String challengeToken) { this.challengeToken = challengeToken; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getRecoveryCode() { return recoveryCode; }
    public void setRecoveryCode(String recoveryCode) { this.recoveryCode = recoveryCode; }
}
