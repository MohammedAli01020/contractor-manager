import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";

function backendUrl() {
  const value = process.env.BACKEND_API_URL || "http://localhost:8081/api/v1";
  return value.replace(/\/+$/, "");
}

export async function POST(request: NextRequest) {
  const body = await request.json().catch(() => null) as Record<string, unknown> | null;
  if (!body || typeof body.challengeToken !== "string" || !body.challengeToken) {
    return NextResponse.json({ message: "جلسة إعداد التحقق غير صالحة." }, { status: 400 });
  }
  try {
    const upstream = await fetch(`${backendUrl()}/platform/auth/mfa/setup`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ challengeToken: body.challengeToken }),
      cache: "no-store",
      signal: AbortSignal.timeout(10_000),
    });
    const payload = await upstream.json().catch(() => ({}));
    if (!upstream.ok) return NextResponse.json({ message: "تعذر بدء إعداد التحقق بخطوتين. سجّل الدخول من جديد." }, { status: upstream.status });
    return NextResponse.json(payload, { headers: { "Cache-Control": "no-store" } });
  } catch {
    return NextResponse.json({ message: "تعذر الاتصال بالخادم." }, { status: 502 });
  }
}
