package com.contractor.app.platform.api.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class PlatformMfaCodeRequest {
    @NotBlank private String challengeToken;
    @NotBlank @Pattern(regexp = "\\d{6}") private String code;
    public String getChallengeToken() { return challengeToken; }
    public void setChallengeToken(String challengeToken) { this.challengeToken = challengeToken; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
