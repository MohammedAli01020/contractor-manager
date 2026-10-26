import { cookies } from "next/headers";
import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

export async function GET() {
  const store = await cookies();
  const token = store.get("platform_owner_token")?.value;
  const username = store.get("platform_owner_username")?.value;
  return NextResponse.json({
    authenticated: Boolean(token),
    ...(token && username ? { username } : {}),
  }, { headers: { "Cache-Control": "no-store" } });
}
