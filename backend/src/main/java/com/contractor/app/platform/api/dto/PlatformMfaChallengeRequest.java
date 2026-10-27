package com.contractor.app.platform.api.dto;

import javax.validation.constraints.NotBlank;

public class PlatformMfaChallengeRequest {
    @NotBlank private String challengeToken;
    public String getChallengeToken() { return challengeToken; }
    public void setChallengeToken(String challengeToken) { this.challengeToken = challengeToken; }
}
