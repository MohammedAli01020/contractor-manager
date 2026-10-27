import { cookies } from "next/headers";
import { NextResponse } from "next/server";

export const runtime = "nodejs";

function backendUrl() {
  return (process.env.BACKEND_API_URL || "http://localhost:8081/api/v1").replace(/\/+$/, "");
}

export async function POST(_request: Request, context: { params: Promise<{ tenantId: string }> }) {
  const token = (await cookies()).get("platform_owner_token")?.value;
  if (!token) return NextResponse.json({ message: "سجّل الدخول أولًا." }, { status: 401 });
  const { tenantId } = await context.params;
  if (!/^\d+$/.test(tenantId) || Number(tenantId) <= 0) {
    return NextResponse.json({ message: "رقم الشركة غير صالح." }, { status: 400 });
  }
  let upstream: Response;
  try {
    upstream = await fetch(`${backendUrl()}/platform/tenants/${tenantId}/admin-activation/resend`, {
      method: "POST",
      headers: { Accept: "application/json", Authorization: `Bearer ${token}` },
      cache: "no-store",
      signal: AbortSignal.timeout(15000),
    });
  } catch {
    return NextResponse.json({ message: "تعذر الاتصال بالـ Backend." }, { status: 502 });
  }
  if (upstream.status === 401 || upstream.status === 403) {
    const response = NextResponse.json({ message: "الجلسة غير صالحة أو ليس لديك تصريح." }, { status: upstream.status });
    response.cookies.set("platform_owner_token", "", { path: "/", maxAge: 0 });
    response.cookies.set("platform_owner_username", "", { path: "/", maxAge: 0 });
    return response;
  }
  if (upstream.ok) return NextResponse.json({ deliveryStatus: "EMAIL_SENT" }, { headers: { "Cache-Control": "no-store" } });
  let message = "تعذر إعادة إرسال رابط التفعيل.";
  try {
    const payload = await upstream.json() as { message?: unknown };
    if (typeof payload.message === "string") message = payload.message;
  } catch { /* Keep safe fallback. */ }
  return NextResponse.json({ message }, { status: upstream.status >= 400 && upstream.status < 500 ? upstream.status : 502 });
}
