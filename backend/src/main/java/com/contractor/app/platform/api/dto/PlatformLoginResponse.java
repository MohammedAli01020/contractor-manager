package com.contractor.app.platform.api.dto;

public class PlatformLoginResponse {
 private final String accessToken;
 private final String tokenType="Bearer";
 private final long expiresInSeconds;
 private final Long ownerId;
 private final String username;
 private final String status;
 private final String challengeToken;

 public PlatformLoginResponse(String token,long ttl,Long id,String username){
  this.accessToken=token;this.expiresInSeconds=ttl;this.ownerId=id;this.username=username;
  this.status="AUTHENTICATED";this.challengeToken=null;
 }
 public PlatformLoginResponse(String status,String challengeToken,Long id,String username){
  this.accessToken=null;this.expiresInSeconds=300;this.ownerId=id;this.username=username;
  this.status=status;this.challengeToken=challengeToken;
 }
 public String getAccessToken(){return accessToken;}
 public String getTokenType(){return tokenType;}
 public long getExpiresInSeconds(){return expiresInSeconds;}
 public Long getOwnerId(){return ownerId;}
 public String getUsername(){return username;}
 public String getStatus(){return status;}
 public String getChallengeToken(){return challengeToken;}
}
