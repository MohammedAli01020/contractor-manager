package com.contractor.app.platform.api.dto;
public class PlatformLoginResponse {
 private final String accessToken; private final String tokenType="Bearer"; private final long expiresInSeconds;
 private final Long ownerId; private final String username;
 public PlatformLoginResponse(String token,long ttl,Long id,String username){this.accessToken=token;this.expiresInSeconds=ttl;this.ownerId=id;this.username=username;}
 public String getAccessToken(){return accessToken;} public String getTokenType(){return tokenType;}
 public long getExpiresInSeconds(){return expiresInSeconds;} public Long getOwnerId(){return ownerId;} public String getUsername(){return username;}
}