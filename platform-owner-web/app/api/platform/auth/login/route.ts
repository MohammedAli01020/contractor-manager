import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";
const TOKEN_COOKIE = "platform_owner_token";
const USER_COOKIE = "platform_owner_username";
const MAX_TOKEN_TTL = 60 * 60 * 24;

function backendUrl() {
  const value = process.env.BACKEND_API_URL || "http://localhost:8080/api/v1";
  return value.replace(/\/+$/, "");
}

export async function POST(request: NextRequest) {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ message: "البيانات المرسلة غير صالحة." }, { status: 400 });
  }

  if (!body || typeof body !== "object") {
    return NextResponse.json({ message: "أدخل اسم المستخدم وكلمة المرور." }, { status: 400 });
  }
  const input = body as Record<string, unknown>;
  if (typeof input.username !== "string" || !input.username.trim() ||
      typeof input.password !== "string" || !input.password) {
    return NextResponse.json({ message: "أدخل اسم المستخدم وكلمة المرور." }, { status: 400 });
  }

  let upstream: Response;
  try {
    upstream = await fetch(`${backendUrl()}/platform/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ username: input.username.trim(), password: input.password }),
      cache: "no-store",
      signal: AbortSignal.timeout(10_000),
    });
  } catch {
    return NextResponse.json({ message: "تعذر الاتصال بالـ Backend. تأكد أنه يعمل على المنفذ 8080." }, { status: 502 });
  }

  const payload = await upstream.json().catch(() => ({})) as Record<string, unknown>;
  if (!upstream.ok) {
    const status = upstream.status === 401 || upstream.status === 403 ? upstream.status : 502;
    return NextResponse.json({ message: "فشل تسجيل الدخول. راجع بيانات الحساب وحالة الخادم." }, { status });
  }
  if (typeof payload.accessToken !== "string" || !payload.accessToken ||
      typeof payload.username !== "string" || typeof payload.expiresInSeconds !== "number") {
    return NextResponse.json({ message: "استجابة تسجيل الدخول من الخادم غير مكتملة." }, { status: 502 });
  }

  const ttl = Math.max(1, Math.min(Math.floor(payload.expiresInSeconds), MAX_TOKEN_TTL));
  const response = NextResponse.json({ authenticated: true, username: payload.username });
  const cookieOptions = {
    httpOnly: true,
    secure: request.nextUrl.protocol === "https:",
    sameSite: "lax" as const,
    path: "/",
    maxAge: ttl,
  };
  response.cookies.set(TOKEN_COOKIE, payload.accessToken, cookieOptions);
  response.cookies.set(USER_COOKIE, payload.username.slice(0, 150), {
    httpOnly: true,
    secure: request.nextUrl.protocol === "https:",
    sameSite: "lax",
    path: "/",
    maxAge: ttl,
  });
  return response;
}
