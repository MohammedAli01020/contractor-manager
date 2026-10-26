import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";
const MAX_RESPONSE_BYTES = 32_768;

function backendUrl() {
  let value = process.env.BACKEND_API_URL || "http://localhost:8081/api/v1";
  while (value.endsWith("/")) value = value.slice(0, -1);
  return value;
}

export async function GET() {
  const cookieStore = await cookies();
  const token = cookieStore.get("platform_owner_token")?.value;
  if (!token) return NextResponse.json({ message: "سجّل الدخول أولًا." }, { status: 401 });

  let upstream: Response;
  try {
    upstream = await fetch(`${backendUrl()}/platform/tenants`, {
      method: "GET",
      headers: { Accept: "application/json", Authorization: `Bearer ${token}` },
      cache: "no-store",
      signal: AbortSignal.timeout(15_000),
    });
  } catch {
    return NextResponse.json({ message: "تعذر الاتصال بالـ Backend. تأكد من عنوان BACKEND_API_URL ومن أن الخادم يعمل." }, { status: 502 });
  }

  if (upstream.status === 401 || upstream.status === 403) {
    const response = NextResponse.json({ message: "الجلسة غير صالحة أو ليس لديك تصريح." }, { status: upstream.status });
    response.cookies.set("platform_owner_token", "", { path: "/", maxAge: 0 });
    response.cookies.set("platform_owner_username", "", { path: "/", maxAge: 0 });
    return response;
  }
  const contentLength = Number(upstream.headers.get("content-length") || 0);
  if (contentLength > MAX_RESPONSE_BYTES) return NextResponse.json({ message: "استجابة الخادم أكبر من المتوقع." }, { status: 502 });
  const text = await upstream.text();
  if (text.length > MAX_RESPONSE_BYTES) return NextResponse.json({ message: "استجابة الخادم أكبر من المتوقع." }, { status: 502 });

  let payload: unknown;
  try {
    payload = text ? JSON.parse(text) : [];
  } catch {
    return NextResponse.json({ message: "تعذر قراءة استجابة الخادم." }, { status: 502 });
  }
  if (!upstream.ok) return NextResponse.json({ message: "تعذر تحميل الشركات من الخادم." }, { status: upstream.status >= 400 && upstream.status < 500 ? upstream.status : 502 });
  if (!Array.isArray(payload) || payload.some((item) => !item || typeof item.tenantId !== "number" || typeof item.tenantName !== "string" || typeof item.status !== "string")) {
    return NextResponse.json({ message: "استجابة قائمة الشركات من الخادم غير مكتملة." }, { status: 502 });
  }
  return NextResponse.json({ tenants: payload }, { headers: { "Cache-Control": "no-store" } });
}

export async function POST(request: NextRequest) {
  const cookieStore = await cookies();
  const token = cookieStore.get("platform_owner_token")?.value;
  if (!token) {
    return NextResponse.json({ message: "سجّل الدخول أولًا." }, { status: 401 });
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ message: "البيانات المرسلة غير صالحة." }, { status: 400 });
  }
  if (!body || typeof body !== "object") {
    return NextResponse.json({ message: "أدخل بيانات الشركة والمدير." }, { status: 400 });
  }

  let upstream: Response;
  try {
    upstream = await fetch(`${backendUrl()}/platform/tenants`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(body),
      cache: "no-store",
      signal: AbortSignal.timeout(15_000),
    });
  } catch {
    return NextResponse.json({ message: "تعذر الاتصال بالـ Backend. تأكد من عنوان BACKEND_API_URL ومن أن الخادم يعمل." }, { status: 502 });
  }

  if (upstream.status === 401 || upstream.status === 403) {
    const response = NextResponse.json({ message: "الجلسة غير صالحة أو ليس لديك تصريح." }, { status: upstream.status });
    response.cookies.set("platform_owner_token", "", { path: "/", maxAge: 0 });
    response.cookies.set("platform_owner_username", "", { path: "/", maxAge: 0 });
    return response;
  }
  const contentLength = Number(upstream.headers.get("content-length") || 0);
  if (contentLength > MAX_RESPONSE_BYTES) {
    return NextResponse.json({ message: "استجابة الخادم أكبر من المتوقع." }, { status: 502 });
  }
  const text = await upstream.text();
  if (text.length > MAX_RESPONSE_BYTES) {
    return NextResponse.json({ message: "استجابة الخادم أكبر من المتوقع." }, { status: 502 });
  }
  let payload: Record<string, unknown> = {};
  try {
    payload = text ? JSON.parse(text) as Record<string, unknown> : {};
  } catch {
    return NextResponse.json({ message: "تعذر قراءة استجابة الخادم." }, { status: 502 });
  }
  if (!upstream.ok) {
    return NextResponse.json({ message: "لم يتم إنشاء الشركة. راجع البيانات أو سجلات الخادم." }, { status: upstream.status >= 400 && upstream.status < 500 ? upstream.status : 502 });
  }
  if (typeof payload.tenantId !== "number" || typeof payload.tenantName !== "string" ||
      typeof payload.adminUsername !== "string" || typeof payload.activationUrl !== "string" ||
      !payload.activationUrl) {
    return NextResponse.json({ message: "استجابة إنشاء الشركة من الخادم غير مكتملة." }, { status: 502 });
  }
  return NextResponse.json(payload, { status: upstream.status, headers: { "Cache-Control": "no-store" } });
}
