(() => {
  "use strict";

  const API_BASE_URL = window.APP_CONFIG && window.APP_CONFIG.API_BASE_URL;
  const form = document.getElementById("activation-form");
  const passwordInput = document.getElementById("password");
  const confirmInput = document.getElementById("confirm-password");
  const submitButton = document.getElementById("submit-button");
  const statusBox = document.getElementById("status");

  const params = new URLSearchParams(window.location.search);
  const activationToken = params.get("token");

  // Remove the secret from the address bar and browser history immediately.
  if (window.history && window.history.replaceState) {
    window.history.replaceState(null, document.title, window.location.pathname);
  }

  function showStatus(message, type) {
    statusBox.textContent = message;
    statusBox.className = "status " + type;
    statusBox.hidden = false;
  }

  if (!activationToken) {
    form.hidden = true;
    showStatus("رابط التفعيل غير مكتمل. اطلب من مسؤول النظام إرسال رابط جديد.", "error");
    return;
  }

  if (!API_BASE_URL || API_BASE_URL.includes("REPLACE-WITH-YOUR-RAILWAY-DOMAIN")) {
    form.hidden = true;
    showStatus("لم يتم إعداد عنوان الخادم بعد. يرجى التواصل مع مسؤول النظام.", "error");
    return;
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    statusBox.hidden = true;

    const password = passwordInput.value;
    const confirmPassword = confirmInput.value;

    if (password.length < 12 || password.length > 100) {
      showStatus("يجب أن تكون كلمة المرور بين 12 و100 حرف.", "error");
      passwordInput.focus();
      return;
    }

    if (password !== confirmPassword) {
      showStatus("كلمتا المرور غير متطابقتين.", "error");
      confirmInput.focus();
      return;
    }

    submitButton.disabled = true;
    submitButton.textContent = "جارٍ تفعيل الحساب…";

    try {
      const response = await fetch(API_BASE_URL.replace(/\/$/, "") + "/api/v1/platform/admin-activation", {
        method: "POST",
        headers: { "Content-Type": "application/json", "Accept": "application/json" },
        credentials: "omit",
        cache: "no-store",
        body: JSON.stringify({ token: activationToken, password })
      });

      if (response.ok) {
        form.reset();
        form.hidden = true;
        showStatus("تم تفعيل حسابك بنجاح. يمكنك الآن تسجيل الدخول من تطبيق النظام.", "success");
        return;
      }

      if (response.status === 400 || response.status === 401 || response.status === 404 || response.status === 409 || response.status === 410) {
        showStatus("رابط التفعيل غير صالح أو انتهت صلاحيته أو سبق استخدامه. اطلب رابطًا جديدًا من مسؤول النظام.", "error");
      } else {
        showStatus("تعذر إكمال التفعيل الآن. حاول مرة أخرى لاحقًا أو تواصل مع مسؤول النظام.", "error");
      }
    } catch (_) {
      showStatus("تعذر الاتصال بالخادم. تحقق من اتصال الإنترنت ثم حاول مرة أخرى.", "error");
    } finally {
      submitButton.disabled = false;
      submitButton.textContent = "تفعيل الحساب";
    }
  });
})();
