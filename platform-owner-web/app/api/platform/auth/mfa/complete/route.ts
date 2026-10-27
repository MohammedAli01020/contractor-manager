import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";
const TOKEN_COOKIE = "platform_owner_token";
const USER_COOKIE = "platform_owner_username";
const MAX_TOKEN_TTL = 60 * 60 * 24;

function backendUrl() {
  const value = process.env.BACKEND_API_URL || "http://localhost:8081/api/v1";
  return value.replace(/\/+$/, "");
}

export async function POST(request: NextRequest) {
  const body = await request.json().catch(() => null) as Record<string, unknown> | null;
  if (!body || typeof body.challengeToken !== "string" || !body.challengeToken ||
      typeof body.setup !== "boolean" ||
      (body.setup
        ? typeof body.code !== "string" || !/^\d{6}$/.test(body.code)
        : !((typeof body.code === "string" && /^\d{6}$/.test(body.code)) ||
            (typeof body.recoveryCode === "string" && /^[A-Za-z0-9-]{8,40}$/.test(body.recoveryCode))))) {
    return NextResponse.json({ message: body?.setup ? "أدخل رمز التحقق المكوّن من 6 أرقام." : "أدخل رمز المصادقة أو كود استرداد صالحًا." }, { status: 400 });
  }

  try {
    const endpoint = body.setup ? "enable" : "verify";
    const upstream = await fetch(`${backendUrl()}/platform/auth/mfa/${endpoint}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ challengeToken: body.challengeToken, code: body.code || "", recoveryCode: body.recoveryCode || "" }),
      cache: "no-store",
      signal: AbortSignal.timeout(10_000),
    });
    const payload = await upstream.json().catch(() => ({})) as Record<string, unknown>;
    if (!upstream.ok) {
      const status = [400, 401, 403, 429].includes(upstream.status) ? upstream.status : 502;
      return NextResponse.json({ message: status === 429 ? "محاولات كثيرة. انتظر 15 دقيقة ثم حاول مجددًا." : "رمز التحقق غير صحيح أو انتهت الجلسة. حاول مجددًا." }, { status });
    }
    if (typeof payload.accessToken !== "string" || !payload.accessToken ||
        typeof payload.username !== "string" || typeof payload.expiresInSeconds !== "number") {
      return NextResponse.json({ message: "استجابة التحقق من الخادم غير مكتملة." }, { status: 502 });
    }

    const ttl = Math.max(1, Math.min(Math.floor(payload.expiresInSeconds), MAX_TOKEN_TTL));
    const response = NextResponse.json({ authenticated: true, username: payload.username, recoveryCodes: body.setup && Array.isArray(payload.recoveryCodes) ? payload.recoveryCodes : undefined });
    const cookieOptions = {
      httpOnly: true,
      secure: request.nextUrl.protocol === "https:",
      sameSite: "lax" as const,
      path: "/",
      maxAge: ttl,
    };
    response.cookies.set(TOKEN_COOKIE, payload.accessToken, cookieOptions);
    response.cookies.set(USER_COOKIE, payload.username.slice(0, 150), cookieOptions);
    return response;
  } catch {
    return NextResponse.json({ message: "تعذر الاتصال بالـ Backend." }, { status: 502 });
  }
}
