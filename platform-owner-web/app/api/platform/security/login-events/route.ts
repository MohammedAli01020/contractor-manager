import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";
const MAX_RESPONSE_BYTES = 65_536;

function backendUrl() {
  return (process.env.BACKEND_API_URL || "http://localhost:8081/api/v1").replace(/\/+$/, "");
}

export async function GET(request: NextRequest) {
  const cookieStore = await cookies();
  const token = cookieStore.get("platform_owner_token")?.value;
  if (!token) return NextResponse.json({ message: "سجّل الدخول أولًا." }, { status: 401 });

  const params = request.nextUrl.searchParams;
  const type = params.get("type") === "PLATFORM_OWNER" ? "PLATFORM_OWNER" : "TENANT_USER";
  const page = Math.max(0, Math.min(100000, Number.parseInt(params.get("page") || "0", 10) || 0));
  const size = Math.max(1, Math.min(100, Number.parseInt(params.get("size") || "25", 10) || 25));

  let upstream: Response;
  try {
    upstream = await fetch(
      `${backendUrl()}/platform/security/login-events?type=${type}&page=${page}&size=${size}`,
      {
        method: "GET",
        headers: { Accept: "application/json", Authorization: `Bearer ${token}` },
        cache: "no-store",
        signal: AbortSignal.timeout(15_000),
      },
    );
  } catch {
    return NextResponse.json({ message: "تعذر الاتصال بالـ Backend." }, { status: 502 });
  }

  if (upstream.status === 401 || upstream.status === 403) {
    const response = NextResponse.json({ message: "الجلسة غير صالحة أو ليس لديك تصريح." }, { status: upstream.status });
    response.cookies.set("platform_owner_token", "", { path: "/", maxAge: 0 });
    response.cookies.set("platform_owner_username", "", { path: "/", maxAge: 0 });
    return response;
  }

  const length = Number(upstream.headers.get("content-length") || 0);
  if (length > MAX_RESPONSE_BYTES) return NextResponse.json({ message: "استجابة الخادم أكبر من المتوقع." }, { status: 502 });
  const text = await upstream.text();
  if (text.length > MAX_RESPONSE_BYTES) return NextResponse.json({ message: "استجابة الخادم أكبر من المتوقع." }, { status: 502 });

  let payload: unknown;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    return NextResponse.json({ message: "تعذر قراءة استجابة الخادم." }, { status: 502 });
  }
  if (!upstream.ok) return NextResponse.json({ message: "تعذر تحميل سجل محاولات الدخول." }, { status: 502 });
  if (!payload || typeof payload !== "object" || !Array.isArray((payload as { items?: unknown }).items)) {
    return NextResponse.json({ message: "استجابة سجل الدخول غير مكتملة." }, { status: 502 });
  }
  return NextResponse.json(payload, { headers: { "Cache-Control": "no-store" } });
}
