"use client";

import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";

type Session = { authenticated: boolean; username?: string };
type TenantSummary = { tenantId: number; tenantName: string; status: "ACTIVE" | "SUSPENDED" | string; createdAt: string; admin: { userId: number; fullName: string; username: string; active: boolean; passwordActivated: boolean } | null };
type CreatedTenant = {
  tenantId: number;
  tenantName: string;
  adminUserId: number;
  adminUsername: string;
  activationUrl: string;
  expiresAt: string;
};

const initialForm = { tenantName: "", adminFullName: "", adminUsername: "", adminPhone: "" };

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
  const [form, setForm] = useState(initialForm);
  const [createBusy, setCreateBusy] = useState(false);
  const [createError, setCreateError] = useState("");
  const [created, setCreated] = useState<CreatedTenant | null>(null);
  const [copied, setCopied] = useState(false);
  const [activeView, setActiveView] = useState<"overview" | "create">("overview");
  const [tenants, setTenants] = useState<TenantSummary[]>([]);
  const [tenantsLoading, setTenantsLoading] = useState(false);
  const [tenantsError, setTenantsError] = useState("");

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
      setSession({ authenticated: true, username: data.username || username.trim() });
      setActiveView("overview");
    } catch (error) {
      setLoginError(error instanceof Error ? error.message : "حدث خطأ غير متوقع.");
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
            <form onSubmit={login} className="form-stack">
              <label htmlFor="owner-username">اسم المستخدم</label>
              <input id="owner-username" name="username" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} placeholder="أدخل اسم المستخدم" required />
              <label htmlFor="owner-password">كلمة المرور</label>
              <input id="owner-password" name="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="أدخل كلمة المرور" required />
              {loginError && <div className="alert alert-error" role="alert">{loginError}</div>}
              <button className="button button-primary button-full" type="submit" disabled={loginBusy}>{loginBusy ? "جارٍ التحقق..." : "دخول آمن"} <Icon name="arrow" /></button>
            </form>
            <div className="login-foot"><span className="status-dot" /> الاتصال يتم عبر الخادم المحلي</div>
          </div>
          <p className="copyright">© {new Date().getFullYear()} مِعمار · إدارة المنصة</p>
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
        <div className="sidebar-bottom"><div className="sidebar-help"><span className="help-icon"><Icon name="shield" /></span><strong>حساب مالك المنصة</strong><p>صلاحيات الإدارة الرئيسية منفصلة عن حسابات الشركات.</p></div><button className="nav-item logout-button" onClick={logout}><Icon name="logout" /> تسجيل الخروج</button></div>
      </aside>
      <section className="workspace">
        <header className="topbar"><div><span className="breadcrumb">المنصة <span>/</span> {activeView === "overview" ? "نظرة عامة" : "إنشاء شركة"}</span><h1>{activeView === "overview" ? "لوحة التحكم" : "إضافة شركة جديدة"}</h1></div><div className="profile"><span className="profile-avatar">{(session.username || "م").slice(0, 1).toUpperCase()}</span><span><strong>{session.username || "مالك المنصة"}</strong><small>مالك المنصة</small></span><span className="online-dot" title="الجلسة نشطة" /></div></header>
        {activeView === "overview" ? (
          <div className="content-area">
            <section className="welcome-banner"><div><span className="eyebrow light-eyebrow">مساحة الإدارة الرئيسية</span><h2>أهلًا بك في مِعمار 👋</h2><p>من هنا تقدر تنشئ شركة جديدة وتجهّز حساب مديرها.</p><button className="button button-white" onClick={() => setActiveView("create")}>إنشاء شركة جديدة <Icon name="arrow" /></button></div><div className="banner-art"><div className="art-orbit orbit-one" /><div className="art-orbit orbit-two" /><div className="art-building"><Icon name="building" /></div><span className="art-spark spark-one">✦</span><span className="art-spark spark-two">✧</span></div></section>
            <div className="section-heading"><div><h2>إجراءات سريعة</h2><p>اختصارات للمهام الأساسية المتاحة حاليًا.</p></div></div>
            <div className="quick-grid">
              <button className="quick-card" onClick={() => setActiveView("create")}><span className="quick-icon purple"><Icon name="building" /></span><span className="quick-copy"><strong>إنشاء شركة</strong><small>إضافة شركة وحساب مديرها الأول</small></span><span className="quick-arrow"><Icon name="arrow" /></span></button>
              <div className="quick-card static-card"><span className="quick-icon green"><Icon name="users" /></span><span className="quick-copy"><strong>تفعيل مدير الشركة</strong><small>يختار المدير كلمة مروره من رابط التفعيل</small></span><span className="mini-tag">رابط آمن</span></div>
            </div>
            <section className="tenants-panel panel">
              <div className="tenants-heading"><div><h2>الشركات المسجلة</h2><p>قائمة الشركات الموجودة بالفعل في قاعدة البيانات.</p></div><span className="tenant-count">{tenants.length} شركة</span></div>
              {tenantsLoading ? <div className="tenants-message">جارٍ تحميل الشركات...</div> : tenantsError ? <div className="alert alert-error" role="alert">{tenantsError}<button className="button button-ghost" type="button" onClick={() => void loadTenants()}>إعادة المحاولة</button></div> : tenants.length === 0 ? <div className="tenants-message">لا توجد شركات مسجلة في قاعدة البيانات الحالية حتى الآن.</div> : <div className="tenants-table-wrap"><table className="tenants-table"><thead><tr><th>رقم الشركة</th><th>اسم الشركة</th><th>مدير الشركة (Admin)</th><th>حالة الحساب</th><th>حالة التفعيل</th><th>حالة الشركة</th><th>تاريخ الإنشاء</th></tr></thead><tbody>{tenants.map((tenant) => <tr key={tenant.tenantId}><td>#{tenant.tenantId}</td><td className="tenant-name-cell">{tenant.tenantName}</td><td>{tenant.admin ? <div className="tenant-admin-cell"><strong>{tenant.admin.fullName}</strong><small dir="ltr">{tenant.admin.username}</small></div> : <span className="muted">لا يوجد Admin مسجل</span>}</td><td>{tenant.admin ? <span className={tenant.admin.active ? "tenant-status status-active" : "tenant-status status-suspended"}>{tenant.admin.active ? "نشط" : "معطّل"}</span> : "—"}</td><td>{tenant.admin ? <span className={tenant.admin.passwordActivated ? "tenant-status status-active" : "tenant-status status-pending"}>{tenant.admin.passwordActivated ? "مفعّل" : "بانتظار التفعيل"}</span> : "—"}</td><td><span className={tenant.status === "ACTIVE" ? "tenant-status status-active" : "tenant-status status-suspended"}>{tenant.status === "ACTIVE" ? "نشطة" : tenant.status === "SUSPENDED" ? "موقوفة" : tenant.status}</span></td><td>{tenant.createdAt ? new Date(tenant.createdAt).toLocaleDateString("ar-EG") : "—"}</td></tr>)}</tbody></table></div>}
              <div className="tenants-footer"><button className="button button-ghost" type="button" onClick={() => void loadTenants()} disabled={tenantsLoading}>تحديث القائمة</button></div>
            </section>
            <div className="bottom-note"><Icon name="shield" /><span>رابط التفعيل يُعرض بعد إنشاء الشركة مرة واحدة؛ شاركه مع المدير المقصود فقط.</span></div>
          </div>
        ) : (
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
                </div>
                {createError && <div className="alert alert-error" role="alert">{createError}</div>}
                <div className="form-actions"><button className="button button-primary" type="submit" disabled={createBusy}>{createBusy ? "جارٍ إنشاء الشركة..." : "إنشاء الشركة وإصدار رابط التفعيل"} <Icon name="arrow" /></button><button className="button button-ghost" type="button" onClick={() => { setForm(initialForm); setCreateError(""); }}>مسح البيانات</button></div>
              </form>
              <aside className="panel process-panel"><span className="process-kicker">ماذا سيحدث؟</span><h3>خطوات آمنة وبسيطة</h3><ol className="process-list"><li><span>1</span><div><strong>إنشاء الشركة</strong><p>يُسجّل الـ Backend الشركة وحساب المدير الأول.</p></div></li><li><span>2</span><div><strong>إصدار رابط مؤقت</strong><p>الرابط صالح لمدة محدودة ويُستخدم مرة واحدة.</p></div></li><li><span>3</span><div><strong>المدير يحدد كلمة المرور</strong><p>لا نُنشئ ولا نرسل كلمة مرور مؤقتة.</p></div></li></ol><div className="process-warning"><Icon name="shield" /><p>احتفظ برابط التفعيل بشكل خاص، فهو بمثابة مفتاح مؤقت للحساب.</p></div></aside>
            </div>
            {created && <section className="panel success-panel" aria-live="polite"><div className="success-heading"><span className="success-icon">✓</span><div><h3>تم إنشاء الشركة بنجاح</h3><p>احفظ رابط التفعيل وشاركه مع المدير المقصود فقط.</p></div></div><div className="result-grid"><div><small>الشركة</small><strong>{created.tenantName}</strong></div><div><small>رقم الشركة</small><strong>#{created.tenantId}</strong></div><div><small>اسم دخول المدير</small><strong>{created.adminUsername}</strong></div><div><small>انتهاء رابط التفعيل</small><strong>{created.expiresAt ? new Date(created.expiresAt).toLocaleString("ar-EG") : "حسب إعداد الخادم"}</strong></div></div><label className="activation-label" htmlFor="activation-link">رابط التفعيل الخاص</label><textarea id="activation-link" className="activation-url" value={created.activationUrl} readOnly rows={3} onFocus={(e) => e.currentTarget.select()} /><div className="activation-actions"><button className="button button-primary" onClick={copyActivationLink} type="button"><Icon name="copy" /> {copied ? "تم النسخ" : "نسخ رابط التفعيل"}</button><button className="button button-ghost" type="button" onClick={() => { setCreated(null); setCopied(false); }}>إخفاء الرابط</button></div><p className="secret-warning">تنبيه: لا تنشر الرابط في مكان عام أو داخل صور الشاشة. إذا أخفيته فلن نحتفظ بنسخة أخرى منه في الواجهة.</p></section>}
          </div>
        )}
      </section>
    </main>
  );
}
