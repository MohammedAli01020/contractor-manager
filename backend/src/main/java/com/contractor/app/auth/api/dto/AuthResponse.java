package com.contractor.app.auth.api.dto;

import com.contractor.app.user.api.dto.UserResponse;

public class AuthResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType = "Bearer";
    private final long expiresInSeconds;
    private final UserResponse user;

    public AuthResponse(String accessToken, String refreshToken, long expiresInSeconds, UserResponse user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresInSeconds = expiresInSeconds;
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public UserResponse getUser() {
        return user;
    }
}
