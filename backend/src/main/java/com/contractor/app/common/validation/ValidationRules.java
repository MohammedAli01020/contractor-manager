package com.contractor.app.common.validation;

public final class ValidationRules {

    private ValidationRules() {
    }

    public static final String USERNAME_REGEX = "^[A-Za-z0-9._@-]{3,60}$";
    public static final String USERNAME_MESSAGE =
            "اسم المستخدم من 3 إلى 60 حرفًا: حروف إنجليزية وأرقام و (. _ @ -) فقط";

    public static final String PASSWORD_REGEX = "^(?=.*\\p{L})(?=.*\\d).{8,64}$";
    public static final String PASSWORD_MESSAGE =
            "كلمة المرور من 8 إلى 64 حرفًا وتحتوي على حرف ورقم على الأقل";
}
