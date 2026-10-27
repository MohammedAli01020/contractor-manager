package com.contractor.app.platform.api.dto;

public class PlatformMfaSetupResponse {
    private final String secret;
    private final String provisioningUri;
    private final String message = "أضف الحساب إلى تطبيق المصادقة ثم أدخل الرمز الظاهر فيه لإكمال التفعيل.";
    public PlatformMfaSetupResponse(String secret, String provisioningUri) {
        this.secret = secret;
        this.provisioningUri = provisioningUri;
    }
    public String getSecret() { return secret; }
    public String getProvisioningUri() { return provisioningUri; }
    public String getMessage() { return message; }
}
