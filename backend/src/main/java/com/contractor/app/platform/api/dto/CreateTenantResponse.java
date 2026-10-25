package com.contractor.app.platform.api.dto;
public class CreateTenantResponse {
 private final Long tenantId; private final String tenantName; private final Long adminUserId; private final String adminUsername;
 private final String activationUrl; private final String expiresAt;
 public CreateTenantResponse(Long tid,String tn,Long uid,String un,String url,String expiry){tenantId=tid;tenantName=tn;adminUserId=uid;adminUsername=un;activationUrl=url;expiresAt=expiry;}
 public Long getTenantId(){return tenantId;} public String getTenantName(){return tenantName;} public Long getAdminUserId(){return adminUserId;}
 public String getAdminUsername(){return adminUsername;} public String getActivationUrl(){return activationUrl;} public String getExpiresAt(){return expiresAt;}
}
