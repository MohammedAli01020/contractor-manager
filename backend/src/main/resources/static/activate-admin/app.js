(() => {
  "use strict";

  const form = document.getElementById("activation-form");
  const passwordInput = document.getElementById("password");
  const confirmInput = document.getElementById("confirm-password");
  const submitButton = document.getElementById("submit-button");
  const statusBox = document.getElementById("status");
  const activationToken = new URLSearchParams(window.location.search).get("token");

  // Remove the bearer token from the visible URL and browser history as early as possible.
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
      const response = await fetch("/api/v1/platform/admin-activation", {
        method: "POST",
        headers: { "Content-Type": "application/json", "Accept": "application/json" },
        credentials: "same-origin",
        cache: "no-store",
        body: JSON.stringify({ token: activationToken, password })
      });
      if (response.ok) {
        form.reset();
        form.hidden = true;
        showStatus("تم تفعيل حسابك بنجاح. يمكنك الآن تسجيل الدخول من تطبيق النظام.", "success");
      } else if ([400, 401, 404, 409, 410].includes(response.status)) {
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
