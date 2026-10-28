"use client";

import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { QRCodeSVG } from "qrcode.react";

type Session = { authenticated: boolean; username?: string };
type TenantSummary = { tenantId: number; tenantName: string; status: "ACTIVE" | "SUSPENDED" | string; createdAt: string; updatedAt?: string | null; totalUsers?: number; activeUsers?: number; admin: { userId: number; fullName: string; username: string; active: boolean; passwordActivated: boolean; phone?: string | null; email?: string | null; emailVerified?: boolean; lastLoginAt?: string | null } | null };
type LoginEventType = "TENANT_USER" | "PLATFORM_OWNER";
type LoginEvent = { id: number; principalType: LoginEventType; principalId: number | null; tenantId: number | null; username: string; outcome: "SUCCESS" | "FAILURE" | "MFA_CHALLENGE"; reason: string | null; clientIp: string | null; requestId: string | null; createdAt: string };
type LoginEventPage = { items: LoginEvent[]; page: number; size: number; totalElements: number; totalPages: number };
type CreatedTenant = {
  tenantId: number;
  tenantName: string;
  adminUserId: number;
  adminUsername: string;
  activationUrl: string | null;
  expiresAt: string;
  deliveryStatus: "EMAIL_SENT" | "DEV_LINK" | string;
};

const initialForm = { tenantName: "", adminFullName: "", adminUsername: "", adminPhone: "", adminEmail: "" };

function Icon({ name }: { name: "grid" | "building" | "shield" | "logout" | "plus" | "copy" | "arrow" | "users" }) {
  const paths: Record<typeof name, string> = {
    grid: "M3 3h7v7H3z M14 3h7v7h-7z M14 14h7v7h-7z M3 14h7v7H3z",
    building: "M3 21h18 M5 21V5l7-2 7 2v16 M9 8h.01 M15 8h.01 M9 12h.01 M15 12h.01 M9 16h.01 M15 16h.01",
    shield: "M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11z M9 12l2 2 4-4",
    logout: "M10 17l5-5-5-5 M15 12H3 M12 3h7a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-7",
    plus: "M12 5v14 M5 12h14",
    copy: "M8 8h12v12H8z M16 8V4H4v12h4",
    arrow: "M5 12h14 M13 6l6 6-6 6",
    users: "M16 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2 M10 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M20 8v6 M23 11h-6",
  };
  return <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><path d={paths[name]} /></svg>;
}

export default function PlatformOwnerApp() {
  const [session, setSession] = useState<Session | null>(null);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loginBusy, setLoginBusy] = useState(false);
  const [loginError, setLoginError] = useState("");
  const [mfaChallenge, setMfaChallenge] = useState("");
  const [mfaMode, setMfaMode] = useState<"setup" | "verify" | null>(null);
  const [mfaSecret, setMfaSecret] = useState("");
  const [mfaUri, setMfaUri] = useState("");
  const [mfaCode, setMfaCode] = useState("");
  const [mfaRecoveryCode, setMfaRecoveryCode] = useState("");
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const [form, setForm] = useState(initialForm);
  const [createBusy, setCreateBusy] = useState(false);
  const [createError, setCreateError] = useState("");
  const [created, setCreated] = useState<CreatedTenant | null>(null);
  const [copied, setCopied] = useState(false);
  const [activeView, setActiveView] = useState<"overview" | "create" | "details" | "security">("overview");
  const [loginEventType, setLoginEventType] = useState<LoginEventType>("TENANT_USER");
  const [loginEvents, setLoginEvents] = useState<LoginEventPage | null>(null);
  const [loginEventsPage, setLoginEventsPage] = useState(0);
  const [loginEventsLoading, setLoginEventsLoading] = useState(false);
  const [loginEventsError, setLoginEventsError] = useState("");
  const [selectedTenant, setSelectedTenant] = useState<TenantSummary | null>(null);
  const [tenants, setTenants] = useState<TenantSummary[]>([]);
  const [tenantsLoading, setTenantsLoading] = useState(false);
  const [tenantsError, setTenantsError] = useState("");
  const [tenantSearch, setTenantSearch] = useState("");
  const [tenantStatusFilter, setTenantStatusFilter] = useState<"ALL" | "ACTIVE" | "SUSPENDED">("ALL");
  const [statusBusy, setStatusBusy] = useState(false);
  const [resendBusy, setResendBusy] = useState(false);
  const [statusError, setStatusError] = useState("");

  const activeTenantCount = tenants.filter((tenant) => tenant.status === "ACTIVE").length;
  const suspendedTenantCount = tenants.filter((tenant) => tenant.status === "SUSPENDED").length;
  const pendingAdminCount = tenants.filter((tenant) => tenant.admin && !tenant.admin.passwordActivated).length;
  const filteredTenants = tenants.filter((tenant) => {
    const query = tenantSearch.trim().toLocaleLowerCase();
    const matchesSearch = !query ||
      tenant.tenantName.toLocaleLowerCase().includes(query) ||
      String(tenant.tenantId).includes(query) ||
      (tenant.admin?.fullName || "").toLocaleLowerCase().includes(query) ||
      (tenant.admin?.username || "").toLocaleLowerCase().includes(query);
    const matchesStatus = tenantStatusFilter === "ALL" || tenant.status === tenantStatusFilter;
    return matchesSearch && matchesStatus;
  });

  useEffect(() => {
    let active = true;
    fetch("/api/platform/session", { cache: "no-store" })
      .then((response) => response.ok ? response.json() : { authenticated: false })
      .then((data: Session) => { if (active) setSession(data); })
      .catch(() => { if (active) setSession({ authenticated: false }); });
    return () => { active = false; };
  }, []);

  const loadTenants = useCallback(async () => {
    setTenantsLoading(true);
    setTenantsError("");
    try {
      const response = await fetch("/api/platform/tenants", { cache: "no-store" });
      const data = await response.json().catch(() => ({}));
      if (response.status === 401 || response.status === 403) {
        setSession({ authenticated: false });
        throw new Error("انتهت صلاحية الجلسة. سجّل الدخول مرة أخرى.");
      }
      if (!response.ok || !Array.isArray(data.tenants)) {
        throw new Error(data.message || "تعذر تحميل الشركات من الخادم.");
      }
      setTenants(data.tenants as TenantSummary[]);
    } catch (error) {
      setTenantsError(error instanceof Error ? error.message : "حدث خطأ أثناء تحميل الشركات.");
    } finally {
      setTenantsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (session?.authenticated) Promise.resolve().then(() => loadTenants());
  }, [session?.authenticated, loadTenants]);

  const loadLoginEvents = useCallback(async () => {
    setLoginEventsLoading(true);
    setLoginEventsError("");
    setLoginEvents(null);
    try {
      const query = new URLSearchParams({
        type: loginEventType,
        page: String(loginEventsPage),
        size: "25",
      });
      const response = await fetch(`/api/platform/security/login-events?${query.toString()}`, { cache: "no-store" });
      const data = await response.json().catch(() => ({}));
      if (response.status === 401 || response.status === 403) {
        setSession({ authenticated: false });
        throw new Error("انتهت صلاحية الجلسة. سجّل الدخول مرة أخرى.");
      }
      if (!response.ok || !Array.isArray(data.items)) {
        throw new Error(data.message || "تعذر تحميل سجل محاولات الدخول.");
      }
      setLoginEvents(data as LoginEventPage);
    } catch (error) {
      setLoginEventsError(error instanceof Error ? error.message : "حدث خطأ أثناء تحميل سجل الدخول.");
    } finally {
      setLoginEventsLoading(false);
    }
  }, [loginEventType, loginEventsPage]);

  useEffect(() => {
    if (session?.authenticated && activeView === "security") {
      Promise.resolve().then(() => loadLoginEvents());
    }
  }, [session?.authenticated, activeView, loadLoginEvents]);

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoginBusy(true);
    setLoginError("");
    try {
      const response = await fetch("/api/platform/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: username.trim(), password }),
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.message || "تعذر تسجيل الدخول. راجع البيانات وحالة الخادم.");
      setPassword("");
      if (data.challengeToken && (data.setupRequired || data.mfaRequired)) {
        setMfaChallenge(data.challengeToken);
        setMfaMode(data.setupRequired ? "setup" : "verify");
        setLoginError("");
        if (data.setupRequired) {
          const setupResponse = await fetch("/api/platform/auth/mfa/setup", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ challengeToken: data.challengeToken }),
          });
          const setupData = await setupResponse.json().catch(() => ({}));
          if (!setupResponse.ok) throw new Error(setupData.message || "تعذر إعداد التحقق بخطوتين.");
          setMfaSecret(setupData.secret || "");
          setMfaUri(setupData.provisioningUri || "");
        }
        return;
      }
      setSession({ authenticated: true, username: data.username || username.trim() });
      setActiveView("overview");
    } catch (error) {
      setLoginError(error instanceof Error ? error.message : "حدث خطأ غير متوقع.");
    } finally {
      setLoginBusy(false);
    }
  }

  async function completeMfa(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!mfaChallenge || !mfaMode) return;
    setLoginBusy(true);
    setLoginError("");
    try {
      const response = await fetch("/api/platform/auth/mfa/complete", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ challengeToken: mfaChallenge, code: mfaCode, recoveryCode: mfaRecoveryCode, setup: mfaMode === "setup" }),
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.message || "تعذر التحقق من الرمز.");
      setMfaChallenge("");
      setMfaMode(null);
      setMfaSecret("");
      setMfaUri("");
      setMfaCode("");
      setMfaRecoveryCode("");
      if (mfaMode === "setup" && Array.isArray(data.recoveryCodes)) setRecoveryCodes(data.recoveryCodes.filter((value: unknown): value is string => typeof value === "string"));
      setSession({ authenticated: true, username: data.username || username.trim() });
      setActiveView("overview");
    } catch (error) {
      setLoginError(error instanceof Error ? error.message : "حدث خطأ أثناء التحقق.");
    } finally {
      setLoginBusy(false);
    }
  }

  async function logout() {
    await fetch("/api/platform/auth/logout", { method: "POST" }).catch(() => undefined);
    setSession({ authenticated: false });
    setCreated(null);
    setForm(initialForm);
    setLoginError("");
    setMfaChallenge("");
    setMfaMode(null);
    setMfaSecret("");
    setMfaUri("");
    setMfaCode("");
    setMfaRecoveryCode("");
    setRecoveryCodes([]);
    setCreateError("");
  }

  async function createTenant(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreateBusy(true);
    setCreateError("");
    setCreated(null);
    setCopied(false);
    try {
      const response = await fetch("/api/platform/tenants", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          tenantName: form.tenantName.trim(),
          adminFullName: form.adminFullName.trim(),
          adminUsername: form.adminUsername.trim(),
          adminPhone: form.adminPhone.trim(),
          adminEmail: form.adminEmail.trim(),
        }),
      });
      const data = await response.json().catch(() => ({}));
      if (response.status === 401 || response.status === 403) {
        setSession({ authenticated: false });
        throw new Error("انتهت صلاحية الجلسة أو ليس لديك تصريح. سجّل الدخول مرة أخرى.");
      }
      if (!response.ok) throw new Error(data.message || "لم يتم إنشاء الشركة. راجع البيانات وحالة الـ Backend.");
      setCreated(data as CreatedTenant);
      setForm(initialForm);
      await loadTenants();
    } catch (error) {
      setCreateError(error instanceof Error ? error.message : "حدث خطأ غير متوقع.");
    } finally {
      setCreateBusy(false);
    }
  }

  async function copyActivationLink() {
    if (!created?.activationUrl) return;
    try {
      await navigator.clipboard.writeText(created.activationUrl);
      setCopied(true);
    } catch {
      setCreateError("تعذر النسخ تلقائيًا. حدّد الرابط وانسخه يدويًا.");
    }
  }

  async function resendAdminActivation() {
    if (!selectedTenant?.admin || selectedTenant.admin.passwordActivated || resendBusy) return;
    setResendBusy(true);
    setStatusError("");
    try {
      const response = await fetch(`/api/platform/tenants/${selectedTenant.tenantId}/admin-activation/resend`, { method: "POST" });
      const data = await response.json().catch(() => ({}));
      if (response.status === 401 || response.status === 403) {
        setSession({ authenticated: false });
        throw new Error("انتهت صلاحية الجلسة أو ليس لديك تصريح. سجّل الدخول مرة أخرى.");
      }
      if (!response.ok) throw new Error(data.message || "تعذر إعادة إرسال رابط التفعيل.");
      setStatusError("تم إرسال رابط تفعيل جديد إلى البريد المسجل، وأصبح الرابط السابق غير صالح.");
    } catch (error) {
      setStatusError(error instanceof Error ? error.message : "حدث خطأ أثناء إعادة إرسال التفعيل.");
    } finally {
      setResendBusy(false);
    }
  }

  async function updateTenantStatus() {
    if (!selectedTenant || statusBusy) return;
    const nextStatus = selectedTenant.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
    const action = nextStatus === "SUSPENDED" ? "إيقاف" : "إعادة تفعيل";
    const confirmed = window.confirm(`هل أنت متأكد من ${action} شركة «${selectedTenant.tenantName}»؟ ${nextStatus === "SUSPENDED" ? "سيُمنع مستخدمو الشركة من الوصول إلى الطلبات المحمية." : "سيتمكن مستخدمو الشركة من الدخول مجددًا إذا كانت حساباتهم مفعّلة."}`);
    if (!confirmed) return;

    setStatusBusy(true);
    setStatusError("");
    try {
      const response = await fetch("/api/platform/tenants", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ tenantId: selectedTenant.tenantId, status: nextStatus }),
      });
      const data = await response.json().catch(() => ({}));
      if (response.status === 401 || response.status === 403) {
        setSession({ authenticated: false });
        throw new Error("انتهت صلاحية الجلسة أو ليس لديك تصريح. سجّل الدخول مرة أخرى.");
      }
      if (!response.ok) throw new Error(data.message || "تعذر تغيير حالة الشركة.");
      const updated = { ...selectedTenant, status: nextStatus };
      setSelectedTenant(updated);
      setTenants((current) => current.map((tenant) => tenant.tenantId === updated.tenantId ? updated : tenant));
    } catch (error) {
      setStatusError(error instanceof Error ? error.message : "حدث خطأ أثناء تغيير حالة الشركة.");
    } finally {
      setStatusBusy(false);
    }
  }

  if (session === null) {
    return <main className="loading-screen"><div className="loading-mark">م</div><p>جارٍ تجهيز لوحة إدارة المنصة...</p></main>;
  }

  if (!session.authenticated) {
    return (
      <main className="login-layout">
        <section className="login-aside">
          <div className="brand brand-light"><span className="brand-mark">م</span><span><strong>مِعمار</strong><small>نظام إدارة المقاولات</small></span></div>
          <div className="login-aside-copy">
            <span className="eyebrow light-eyebrow">PLATFORM CONTROL CENTER</span>
            <h1>كل شركاتك،<br />تحت إدارة واحدة.</h1>
            <p>مساحة آمنة لإدارة المنصة وإنشاء حسابات الشركات ومتابعة تفعيل مسؤوليها.</p>
            <div className="security-note"><Icon name="shield" /><span><strong>الوصول لمالك المنصة فقط</strong><small>تسجيل الدخول يتم التحقق منه بواسطة الخادم.</small></span></div>
          </div>
          <div className="aside-footer">إدارة أوضح. تشغيل أكثر أمانًا.</div>
        </section>
        <section className="login-main">
          <div className="login-card">
            <div className="login-icon"><Icon name="shield" /></div>
            <span className="eyebrow">مرحبًا بعودتك</span>
            <h2>تسجيل دخول مالك المنصة</h2>
            <p className="muted">أدخل بيانات الحساب الرئيسي للمتابعة.</p>
            <form onSubmit={mfaMode ? completeMfa : login} className="form-stack">
              {!mfaMode ? (
                <>
                  <label htmlFor="owner-username">اسم المستخدم</label>
                  <input id="owner-username" name="username" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} placeholder="أدخل اسم المستخدم" required />
                  <label htmlFor="owner-password">كلمة المرور</label>
                  <input id="owner-password" name="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="أدخل كلمة المرور" required />
                </>
              ) : (
                <>
                  <h2>{mfaMode === "setup" ? "تفعيل التحقق بخطوتين" : "التحقق بخطوتين"}</h2>
                  <p>{mfaMode === "setup" ? "افتح تطبيق مصادقة مثل Google Authenticator أو Microsoft Authenticator، وأضف الحساب يدويًا باستخدام المفتاح التالي." : "أدخل الرمز الحالي الظاهر في تطبيق المصادقة."}</p>
                  {mfaMode === "setup" && <div className="mfa-setup-details">
                    <p>امسح رمز QR باستخدام Google Authenticator أو Microsoft Authenticator لإضافة الحساب. يظهر الرمز أثناء الإعداد فقط.</p>
                    {mfaUri && <div className="mfa-qr-frame" style={{ display: "flex", justifyContent: "center", padding: "16px", background: "#fff", borderRadius: "12px", width: "fit-content", margin: "12px auto" }}>
                      <QRCodeSVG
                        value={mfaUri}
                        size={260}
                        level="M"
                        marginSize={4}
                        style={{ display: "block", width: "260px", height: "260px", maxWidth: "100%", flex: "0 1 260px" }}
                      />
                    </div>}
                    <details>
                      <summary>تعذّر مسح الرمز؟ عرض المفتاح يدويًا</summary>
                      <label htmlFor="mfa-secret">المفتاح السري للتطبيق</label>
                      <input id="mfa-secret" value={mfaSecret} readOnly onFocus={(e) => e.currentTarget.select()} />
                      <small>أدخل المفتاح يدويًا في تطبيق المصادقة. لا تشاركه مع أي شخص.</small>
                    </details>
                  </div>}
                  {mfaMode === "verify" && <>
                    <label htmlFor="mfa-recovery-code">كود الاسترداد (إذا تعذر استخدام التطبيق)</label>
                    <input id="mfa-recovery-code" autoComplete="off" value={mfaRecoveryCode} onChange={(e) => setMfaRecoveryCode(e.target.value.toUpperCase().slice(0, 40))} placeholder="أدخل كود الاسترداد" />
                    <p className="muted">استخدم رمز التطبيق أو كود الاسترداد، وليس كليهما.</p>
                  </>}
                  <label htmlFor="mfa-code">رمز التحقق (6 أرقام)</label>
                  <input id="mfa-code" inputMode="numeric" autoComplete="one-time-code" maxLength={6} pattern="[0-9]{6}" value={mfaCode} onChange={(e) => setMfaCode(e.target.value.replace(/\D/g, "").slice(0, 6))} placeholder="000000" required={mfaMode === "setup" || !mfaRecoveryCode} disabled={mfaMode === "verify" && !!mfaRecoveryCode} />
                </>
              )}
              {loginError && <div className="alert alert-error" role="alert">{loginError}</div>}
              <button className="button button-primary button-full" type="submit" disabled={loginBusy}>{loginBusy ? "جارٍ التحقق..." : mfaMode === "setup" ? "تفعيل التحقق والدخول" : mfaMode === "verify" ? "تحقق ودخول" : "دخول آمن"} <Icon name="arrow" /></button>
              {mfaMode && <button className="button button-ghost button-full" type="button" disabled={loginBusy} onClick={() => { setMfaMode(null); setMfaChallenge(""); setMfaSecret(""); setMfaUri(""); setMfaCode(""); setLoginError(""); }}>العودة لتسجيل الدخول</button>}
            </form>
            <div className="login-foot"><span className="status-dot" /> الاتصال يتم عبر الخادم المحلي</div>
          </div>
          <p className="copyright">© {new Date().getFullYear()} مِعمار · إدارة المنصة</p>
        </section>
      </main>
    );
  }

  if (recoveryCodes.length > 0) {
    return (
      <main className="login-layout">
        <section className="login-main" style={{ width: "100%", minHeight: "100vh" }}>
          <div className="login-card" style={{ maxWidth: 560, width: "100%" }}>
            <div className="login-icon"><Icon name="shield" /></div>
            <span className="eyebrow">خطوة أمان مهمة</span>
            <h2>احفظ أكواد الاسترداد</h2>
            <p className="muted">هذه الأكواد بدائل للدخول عند فقدان تطبيق المصادقة. كل كود يعمل مرة واحدة فقط، ولن تظهر مرة أخرى. احفظها في مكان آمن ولا تشاركها.</p>
            <div className="mfa-setup-details" dir="ltr" style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
              {recoveryCodes.map((code) => <code key={code} style={{ padding: 10, border: "1px solid var(--border, #ddd)", borderRadius: 8, textAlign: "center" }}>{code}</code>)}
            </div>
            <button className="button button-primary button-full" type="button" onClick={() => { void navigator.clipboard.writeText(recoveryCodes.join("\n")); }}>نسخ الأكواد</button>
            <button className="button button-ghost button-full" type="button" onClick={() => setRecoveryCodes([])}>حفظت الأكواد — متابعة</button>
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark">م</span><span><strong>مِعمار</strong><small>إدارة المقاولات</small></span></div>
        <div className="side-label">القائمة الرئيسية</div>
        <button className={activeView === "overview" ? "nav-item active" : "nav-item"} onClick={() => setActiveView("overview")}><Icon name="grid" /> نظرة عامة</button>
        <button className={activeView === "create" ? "nav-item active" : "nav-item"} onClick={() => setActiveView("create")}><Icon name="building" /> إنشاء شركة</button>
        <button className={activeView === "security" ? "nav-item active" : "nav-item"} onClick={() => { setActiveView("security"); setLoginEventsPage(0); }}><Icon name="shield" /> سجل الدخول</button>
        <div className="sidebar-bottom"><div className="sidebar-help"><span className="help-icon"><Icon name="shield" /></span><strong>حساب مالك المنصة</strong><p>صلاحيات الإدارة الرئيسية منفصلة عن حسابات الشركات.</p></div><button className="nav-item logout-button" onClick={logout}><Icon name="logout" /> تسجيل الخروج</button></div>
      </aside>
      <section className="workspace">
        <header className="topbar"><div><span className="breadcrumb">المنصة <span>/</span> {activeView === "overview" ? "نظرة عامة" : activeView === "create" ? "إنشاء شركة" : activeView === "security" ? "سجل الدخول" : "تفاصيل الشركة"}</span><h1>{activeView === "overview" ? "لوحة التحكم" : activeView === "create" ? "إضافة شركة جديدة" : activeView === "security" ? "سجل محاولات الدخول" : "تفاصيل الشركة"}</h1></div><div className="profile"><span className="profile-avatar">{(session.username || "م").slice(0, 1).toUpperCase()}</span><span><strong>{session.username || "مالك المنصة"}</strong><small>مالك المنصة</small></span><span className="online-dot" title="الجلسة نشطة" /></div></header>
        {activeView === "overview" ? (
          <div className="content-area">
            <section className="welcome-banner"><div><span className="eyebrow light-eyebrow">مساحة الإدارة الرئيسية</span><h2>أهلًا بك في مِعمار 👋</h2><p>من هنا تقدر تنشئ شركة جديدة وتجهّز حساب مديرها.</p><button className="button button-white" onClick={() => setActiveView("create")}>إنشاء شركة جديدة <Icon name="arrow" /></button></div><div className="banner-art"><div className="art-orbit orbit-one" /><div className="art-orbit orbit-two" /><div className="art-building"><Icon name="building" /></div><span className="art-spark spark-one">✦</span><span className="art-spark spark-two">✧</span></div></section>
            <div className="section-heading"><div><h2>إجراءات سريعة</h2><p>اختصارات للمهام الأساسية المتاحة حاليًا.</p></div></div>
            <div className="quick-grid">
              <button className="quick-card" onClick={() => setActiveView("create")}><span className="quick-icon purple"><Icon name="building" /></span><span className="quick-copy"><strong>إنشاء شركة</strong><small>إضافة شركة وحساب مديرها الأول</small></span><span className="quick-arrow"><Icon name="arrow" /></span></button>
              <div className="quick-card static-card"><span className="quick-icon green"><Icon name="users" /></span><span className="quick-copy"><strong>تفعيل مدير الشركة</strong><small>يختار المدير كلمة مروره من رابط التفعيل</small></span><span className="mini-tag">رابط آمن</span></div>
            </div>
            <section className="tenant-metrics" aria-label="ملخص الشركات">
              <article className="tenant-metric"><span>إجمالي الشركات</span><strong>{tenants.length}</strong><small>حسب القائمة المحمّلة</small></article>
              <article className="tenant-metric"><span>الشركات النشطة</span><strong>{activeTenantCount}</strong><small>حالتها ACTIVE</small></article>
              <article className="tenant-metric"><span>الشركات الموقوفة</span><strong>{suspendedTenantCount}</strong><small>حالتها SUSPENDED</small></article>
              <article className="tenant-metric"><span>مديرون بانتظار التفعيل</span><strong>{pendingAdminCount}</strong><small>لم يحددوا كلمة المرور بعد</small></article>
            </section>
            <section className="tenants-panel panel">
              <div className="tenants-heading"><div><h2>الشركات المسجلة</h2><p>ابحث باسم الشركة أو رقمها أو اسم مديرها، ويمكنك تصفية القائمة حسب الحالة.</p></div><span className="tenant-count">{filteredTenants.length} من {tenants.length} شركة</span></div>
              <div className="tenant-filters">
                <label className="tenant-search"><span>بحث</span><input type="search" value={tenantSearch} onChange={(event) => setTenantSearch(event.target.value)} placeholder="اسم الشركة أو المدير أو رقم الشركة" aria-label="البحث في الشركات" /></label>
                <label className="tenant-status-filter"><span>حالة الشركة</span><select value={tenantStatusFilter} onChange={(event) => setTenantStatusFilter(event.target.value as "ALL" | "ACTIVE" | "SUSPENDED")} aria-label="تصفية الشركات حسب الحالة"><option value="ALL">كل الحالات</option><option value="ACTIVE">نشطة</option><option value="SUSPENDED">موقوفة</option></select></label>
              </div>
              {tenantsLoading ? <div className="tenants-message">جارٍ تحميل الشركات...</div> : tenantsError ? <div className="alert alert-error" role="alert">{tenantsError}<button className="button button-ghost" type="button" onClick={() => void loadTenants()}>إعادة المحاولة</button></div> : tenants.length === 0 ? <div className="tenants-message">لا توجد شركات مسجلة في قاعدة البيانات الحالية حتى الآن.</div> : filteredTenants.length === 0 ? <div className="tenants-message">لا توجد نتائج مطابقة. غيّر كلمة البحث أو حالة الشركة.</div> : <div className="tenants-table-wrap"><table className="tenants-table"><thead><tr><th>رقم الشركة</th><th>اسم الشركة</th><th>مدير الشركة (Admin)</th><th>حالة الحساب</th><th>حالة التفعيل</th><th>حالة الشركة</th><th>تاريخ الإنشاء</th></tr></thead><tbody>{filteredTenants.map((tenant) => <tr key={tenant.tenantId}><td>#{tenant.tenantId}</td><td className="tenant-name-cell"><strong>{tenant.tenantName}</strong><button className="tenant-details-link" type="button" onClick={() => { setSelectedTenant(tenant); setActiveView("details"); }}>عرض التفاصيل</button></td><td>{tenant.admin ? <div className="tenant-admin-cell"><strong>{tenant.admin.fullName}</strong><small dir="ltr">{tenant.admin.username}</small></div> : <span className="muted">لا يوجد Admin مسجل</span>}</td><td>{tenant.admin ? <span className={tenant.admin.active ? "tenant-status status-active" : "tenant-status status-suspended"}>{tenant.admin.active ? "نشط" : "معطّل"}</span> : "—"}</td><td>{tenant.admin ? <span className={tenant.admin.passwordActivated ? "tenant-status status-active" : "tenant-status status-pending"}>{tenant.admin.passwordActivated ? "مفعّل" : "بانتظار التفعيل"}</span> : "—"}</td><td><span className={tenant.status === "ACTIVE" ? "tenant-status status-active" : "tenant-status status-suspended"}>{tenant.status === "ACTIVE" ? "نشطة" : tenant.status === "SUSPENDED" ? "موقوفة" : tenant.status}</span></td><td>{tenant.createdAt ? new Date(tenant.createdAt).toLocaleDateString("ar-EG") : "—"}</td></tr>)}</tbody></table></div>}
              <div className="tenants-footer"><button className="button button-ghost" type="button" onClick={() => void loadTenants()} disabled={tenantsLoading}>تحديث القائمة</button></div>
            </section>
            <div className="bottom-note"><Icon name="shield" /><span>رابط التفعيل يُعرض بعد إنشاء الشركة مرة واحدة؛ شاركه مع المدير المقصود فقط.</span></div>
          </div>
        ) : activeView === "create" ? (
          <div className="content-area create-area">
            <div className="section-heading"><div><h2>بيانات الشركة</h2><p>سيتم إنشاء الشركة وحساب المدير الأول من خلال الـ Backend.</p></div><span className="step-pill">خطوة 1 من 2</span></div>
            <div className="create-layout">
              <form className="panel create-form" onSubmit={createTenant}>
                <div className="panel-title"><span className="panel-icon"><Icon name="building" /></span><div><h3>معلومات الشركة</h3><p>اكتب بيانات الشركة كما تريد تسجيلها.</p></div></div>
                <div className="field-grid">
                  <div className="field full"><label htmlFor="tenant-name">اسم الشركة <span>*</span></label><input id="tenant-name" value={form.tenantName} onChange={(e) => setForm({ ...form, tenantName: e.target.value })} maxLength={150} placeholder="مثال: شركة النور للمقاولات" required /><small>حتى 150 حرفًا.</small></div>
                  <div className="field"><label htmlFor="admin-name">اسم المدير بالكامل <span>*</span></label><input id="admin-name" value={form.adminFullName} onChange={(e) => setForm({ ...form, adminFullName: e.target.value })} maxLength={150} placeholder="الاسم بالكامل" required /></div>
                  <div className="field"><label htmlFor="admin-username">اسم دخول المدير <span>*</span></label><input id="admin-username" value={form.adminUsername} onChange={(e) => setForm({ ...form, adminUsername: e.target.value })} autoComplete="off" placeholder="مثال: company_admin" required /><small>لا تشارك كلمة مرور؛ المدير ينشئها بنفسه.</small></div>
                  <div className="field full"><label htmlFor="admin-phone">رقم هاتف المدير <span className="optional">(اختياري)</span></label><input id="admin-phone" type="tel" inputMode="tel" autoComplete="tel" value={form.adminPhone} onChange={(e) => setForm({ ...form, adminPhone: e.target.value })} maxLength={30} placeholder="01xxxxxxxxx" /></div>
                  <div className="field full"><label htmlFor="admin-email">بريد المدير الإلكتروني <span>*</span></label><input id="admin-email" type="email" autoComplete="email" value={form.adminEmail} onChange={(e) => setForm({ ...form, adminEmail: e.target.value })} maxLength={254} placeholder="admin@example.com" required /><small>سيُرسل رابط التفعيل إلى هذا البريد.</small></div>
                </div>
                {createError && <div className="alert alert-error" role="alert">{createError}</div>}
                <div className="form-actions"><button className="button button-primary" type="submit" disabled={createBusy}>{createBusy ? "جارٍ إنشاء الشركة..." : "إنشاء الشركة وإصدار رابط التفعيل"} <Icon name="arrow" /></button><button className="button button-ghost" type="button" onClick={() => { setForm(initialForm); setCreateError(""); }}>مسح البيانات</button></div>
              </form>
              <aside className="panel process-panel"><span className="process-kicker">ماذا سيحدث؟</span><h3>خطوات آمنة وبسيطة</h3><ol className="process-list"><li><span>1</span><div><strong>إنشاء الشركة</strong><p>يُسجّل الـ Backend الشركة وحساب المدير الأول.</p></div></li><li><span>2</span><div><strong>إصدار رابط مؤقت</strong><p>الرابط صالح لمدة محدودة ويُستخدم مرة واحدة.</p></div></li><li><span>3</span><div><strong>المدير يحدد كلمة المرور</strong><p>لا نُنشئ ولا نرسل كلمة مرور مؤقتة.</p></div></li></ol><div className="process-warning"><Icon name="shield" /><p>احتفظ برابط التفعيل بشكل خاص، فهو بمثابة مفتاح مؤقت للحساب.</p></div></aside>
            </div>
            {created && <section className="panel success-panel" aria-live="polite"><div className="success-heading"><span className="success-icon">✓</span><div><h3>تم إنشاء الشركة بنجاح</h3><p>{created.deliveryStatus === "EMAIL_SENT" ? "تم إرسال رابط التفعيل إلى البريد الإلكتروني المسجل." : "وضع التطوير: احفظ رابط التفعيل وشاركه مع المدير المقصود فقط."}</p></div></div><div className="result-grid"><div><small>الشركة</small><strong>{created.tenantName}</strong></div><div><small>رقم الشركة</small><strong>#{created.tenantId}</strong></div><div><small>اسم دخول المدير</small><strong>{created.adminUsername}</strong></div><div><small>انتهاء رابط التفعيل</small><strong>{created.expiresAt ? new Date(created.expiresAt).toLocaleString("ar-EG") : "حسب إعداد الخادم"}</strong></div></div>{created.activationUrl && <><label className="activation-label" htmlFor="activation-link">رابط التفعيل الخاص (وضع التطوير فقط)</label><textarea id="activation-link" className="activation-url" value={created.activationUrl} readOnly rows={3} onFocus={(e) => e.currentTarget.select()} /><div className="activation-actions"><button className="button button-primary" onClick={copyActivationLink} type="button"><Icon name="copy" /> {copied ? "تم النسخ" : "نسخ رابط التفعيل"}</button><button className="button button-ghost" type="button" onClick={() => { setCreated(null); setCopied(false); }}>إخفاء الرابط</button></div><p className="secret-warning">تنبيه: لا تنشر الرابط في مكان عام أو داخل صور الشاشة.</p></>}</section>}
          </div>
        ) : activeView === "details" ? (
          <div className="content-area create-area">
            <div className="section-heading">
              <div><h2>تفاصيل الشركة</h2><p>ملخص بيانات الشركة وحساب مديرها من آخر قائمة تم تحميلها.</p></div>
              <button className="button button-ghost" type="button" onClick={() => setActiveView("overview")}>العودة إلى الشركات</button>
            </div>
            {!selectedTenant ? (
              <div className="panel tenants-message">لم يتم اختيار شركة. ارجع إلى القائمة واختر الشركة التي تريد عرضها.</div>
            ) : (
              <section className="panel tenant-details-panel">
                <div className="tenant-details-header">
                  <div><span className="eyebrow">ملف الشركة</span><h2>{selectedTenant.tenantName}</h2><p>رقم الشركة: #{selectedTenant.tenantId}</p></div>
                  <span className={selectedTenant.status === "ACTIVE" ? "tenant-status status-active" : "tenant-status status-suspended"}>{selectedTenant.status === "ACTIVE" ? "نشطة" : selectedTenant.status === "SUSPENDED" ? "موقوفة" : selectedTenant.status}</span>
                </div>
                <div className="tenant-details-grid">
                  <article><span>رقم الشركة</span><strong>#{selectedTenant.tenantId}</strong></article>
                  <article><span>تاريخ الإنشاء</span><strong>{selectedTenant.createdAt ? new Date(selectedTenant.createdAt).toLocaleString("ar-EG") : "غير متاح"}</strong></article>
                  <article><span>آخر تحديث لبيانات الشركة</span><strong>{selectedTenant.updatedAt ? new Date(selectedTenant.updatedAt).toLocaleString("ar-EG") : "غير متاح"}</strong></article>
                  <article><span>إجمالي حسابات المستخدمين</span><strong>{selectedTenant.totalUsers ?? "غير متاح"}</strong></article>
                  <article><span>الحسابات النشطة والمفعّلة</span><strong>{selectedTenant.activeUsers ?? "غير متاح"}</strong></article>
                  <article><span>حالة الشركة</span><strong>{selectedTenant.status === "ACTIVE" ? "نشطة" : selectedTenant.status === "SUSPENDED" ? "موقوفة" : selectedTenant.status}</strong></article>
                  <article><span>حساب المدير</span><strong>{selectedTenant.admin ? (selectedTenant.admin.active ? "نشط" : "معطّل") : "لا يوجد مدير مسجل"}</strong></article>
                  <article><span>حالة كلمة المرور</span><strong>{selectedTenant.admin ? (selectedTenant.admin.passwordActivated ? "تم التفعيل" : "بانتظار التفعيل") : "غير متاح"}</strong></article>
                  <article><span>آخر دخول ناجح للمدير</span><strong>{selectedTenant.admin?.lastLoginAt ? new Date(selectedTenant.admin.lastLoginAt).toLocaleString("ar-EG") : "لم يسجل دخولًا ناجحًا بعد"}</strong></article>
                </div>
                <div className="tenant-admin-details">
                  <h3>مدير الشركة (Admin)</h3>
                  {selectedTenant.admin ? (
                    <div className="tenant-admin-detail-grid">
                      <div><span>الاسم بالكامل</span><strong>{selectedTenant.admin.fullName}</strong></div>
                      <div><span>اسم المستخدم</span><strong dir="ltr">{selectedTenant.admin.username}</strong></div>
                      <div><span>رقم المستخدم</span><strong>#{selectedTenant.admin.userId}</strong></div>
                      <div><span>رقم الهاتف</span><strong dir="ltr">{selectedTenant.admin.phone || "غير مسجل"}</strong></div>
                      <div><span>البريد الإلكتروني</span><strong dir="ltr">{selectedTenant.admin.email || "غير مسجل"}</strong></div>
                      <div><span>حالة التحقق من البريد</span><strong>{selectedTenant.admin.email ? (selectedTenant.admin.emailVerified ? "تم التحقق" : "لم يتم التحقق") : "لا يوجد بريد مسجل"}</strong></div>
                      <div><span>حالة الحساب</span><strong>{selectedTenant.admin.active ? "نشط" : "معطّل"}</strong></div>
                    </div>
                  ) : <p className="muted">لا توجد بيانات مدير مرتبطة بهذه الشركة في الاستجابة الحالية.</p>}
                </div>
                <div className="bottom-note"><Icon name="shield" /><span>إيقاف الشركة يمنع مستخدميها من الوصول إلى الطلبات المحمية. إعادة التفعيل لا تتجاوز حالة تعطيل حساب المستخدم نفسه.</span></div>
                {statusError && <div className="alert alert-error" role="alert">{statusError}</div>}
                <div className="form-actions">
                  {selectedTenant.admin && !selectedTenant.admin.passwordActivated && <button className="button button-ghost" type="button" onClick={() => void resendAdminActivation()} disabled={resendBusy}>{resendBusy ? "جارٍ إرسال رابط جديد..." : "إعادة إرسال رابط التفعيل"}</button>}
                  <button className={selectedTenant.status === "ACTIVE" ? "button button-danger" : "button button-primary"} type="button" onClick={() => void updateTenantStatus()} disabled={statusBusy}>
                    {statusBusy ? "جارٍ تحديث الحالة..." : selectedTenant.status === "ACTIVE" ? "إيقاف الشركة" : "إعادة تفعيل الشركة"}
                  </button>
                  <button className="button button-ghost" type="button" onClick={() => setActiveView("overview")}>العودة إلى قائمة الشركات</button>
                  <button className="button button-ghost" type="button" onClick={() => void loadTenants()} disabled={tenantsLoading}>تحديث بيانات الشركات</button>
                </div>
              </section>
            )}
          </div>
        ) : (
          <div className="content-area create-area">
            <div className="section-heading">
              <div><h2>سجل محاولات الدخول</h2><p>سجلات أمنية دائمة، مع فصل حساب مالك المنصة عن حسابات مستخدمي الشركات.</p></div>
              <button className="button button-ghost" type="button" onClick={() => void loadLoginEvents()} disabled={loginEventsLoading}>تحديث السجل</button>
            </div>
            <div className="tenant-filters">
              <label className="tenant-status-filter"><span>نوع السجل</span>
                <select value={loginEventType} onChange={(event) => { setLoginEventType(event.target.value as LoginEventType); setLoginEventsPage(0); }} aria-label="نوع سجل الدخول">
                  <option value="TENANT_USER">مستخدمو الشركات</option>
                  <option value="PLATFORM_OWNER">مالك المنصة</option>
                </select>
              </label>
            </div>
            {loginEventsError && <div className="alert alert-error" role="alert">{loginEventsError}</div>}
            {loginEventsLoading ? <div className="panel tenants-message">جارٍ تحميل سجل الدخول...</div> :
              !loginEvents || loginEvents.items.length === 0 ? <div className="panel tenants-message">لا توجد محاولات دخول مسجلة في هذا القسم حتى الآن.</div> :
              <section className="panel tenants-panel">
                <div className="tenants-heading"><div><h2>{loginEventType === "TENANT_USER" ? "محاولات دخول مستخدمي الشركات" : "محاولات دخول مالك المنصة"}</h2><p>عدد السجلات المطابقة: {loginEvents.totalElements}</p></div><span className="tenant-count">صفحة {loginEvents.page + 1} من {Math.max(1, loginEvents.totalPages)}</span></div>
                <div className="tenants-table-wrap"><table className="tenants-table"><thead><tr><th>التاريخ والوقت</th><th>اسم المستخدم</th><th>النتيجة</th><th>الشركة</th><th>عنوان IP</th><th>سبب الفشل / الحالة</th><th>معرّف الطلب</th></tr></thead>
                  <tbody>{loginEvents.items.map((event) => <tr key={event.id}>
                    <td>{event.createdAt ? new Date(event.createdAt).toLocaleString("ar-EG") : "—"}</td>
                    <td dir="ltr">{event.username}</td>
                    <td><span className={event.outcome === "SUCCESS" ? "tenant-status status-active" : event.outcome === "FAILURE" ? "tenant-status status-suspended" : "tenant-status"}>{event.outcome === "SUCCESS" ? "ناجح" : event.outcome === "FAILURE" ? "فاشل" : "بانتظار التحقق"}</span></td>
                    <td>{event.tenantId == null ? (event.principalType === "PLATFORM_OWNER" ? "مالك المنصة" : "غير محددة") : `#${event.tenantId}`}</td>
                    <td dir="ltr">{event.clientIp || "—"}</td>
                    <td>{event.reason || "—"}</td>
                    <td dir="ltr">{event.requestId || "—"}</td>
                  </tr>)}</tbody>
                </table></div>
                <div className="tenants-footer"><span className="muted">عرض {loginEvents.items.length} من {loginEvents.totalElements} سجل</span>
                  <div className="form-actions">
                    <button className="button button-ghost" type="button" onClick={() => setLoginEventsPage((page) => Math.max(0, page - 1))} disabled={loginEventsLoading || loginEvents.page <= 0}>السابق</button>
                    <button className="button button-ghost" type="button" onClick={() => setLoginEventsPage((page) => page + 1)} disabled={loginEventsLoading || loginEvents.page + 1 >= loginEvents.totalPages}>التالي</button>
                  </div>
                </div>
              </section>}
            <div className="bottom-note"><Icon name="shield" /><span>لا يسجّل النظام كلمات المرور أو رموز الجلسات. عنوان IP ومعرّف الطلب مخصصان للمراجعة الأمنية.</span></div>
          </div>
        )}
      </section>
    </main>
  );
}
