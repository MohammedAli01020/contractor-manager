import { NextRequest, NextResponse } from "next/server";

export async function POST(request: NextRequest) {
  const response = NextResponse.json({ authenticated: false });
  const options = {
    httpOnly: true,
    secure: request.nextUrl.protocol === "https:",
    sameSite: "lax" as const,
    path: "/",
    maxAge: 0,
  };
  response.cookies.set("platform_owner_token", "", options);
  response.cookies.set("platform_owner_username", "", options);
  return response;
}
