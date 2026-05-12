import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

export function GET() {
  return NextResponse.json({ status: "UP", timestamp: new Date().toISOString() });
}
