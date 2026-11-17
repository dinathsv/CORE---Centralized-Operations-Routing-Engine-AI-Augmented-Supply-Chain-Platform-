import { NextRequest } from "next/server";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";
const routes: [RegExp, string[]][] = [
  [/^inventory$/, ["GET", "POST"]], [/^inventory\/\d+$/, ["GET", "PUT", "DELETE"]],
  [/^orders$/, ["GET", "POST"]], [/^orders\/\d+$/, ["GET", "DELETE"]],
  [/^orders\/\d+\/status$/, ["PATCH"]], [/^ai\/health$/, ["GET"]], [/^ai\/(query|documents)$/, ["POST"]],
];
async function proxy(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  const route = routes.find(([pattern]) => pattern.test(path.join("/")));
  if (!route) return Response.json({ detail: "API route not found." }, { status: 404 });
  if (!route[1].includes(request.method)) return Response.json({ detail: "Method not allowed." }, { status: 405, headers: { Allow: route[1].join(", ") } });
  const authorization = request.headers.get("authorization");
  if (!authorization?.startsWith("Basic ")) return Response.json({ detail: "Sign in to access the workspace." }, { status: 401 });
  if (Number(request.headers.get("content-length") || 0) > 2_000_000) return Response.json({ detail: "Request body is too large." }, { status: 413 });
  try {
    const url = new URL(`/api/${path.map(encodeURIComponent).join("/")}${request.nextUrl.search}`, process.env.GATEWAY_URL || "http://localhost:8080");
    const body = ["GET", "HEAD"].includes(request.method) ? undefined : await request.text();
    if (body && Buffer.byteLength(body, "utf8") > 2_000_000) return Response.json({ detail: "Request body is too large." }, { status: 413 });
    const response = await fetch(url, { method: request.method, headers: { Authorization: authorization, "Content-Type": "application/json", Accept: "application/json" }, body: body || undefined, cache: "no-store", redirect: "error", signal: AbortSignal.timeout(60_000) });
    return new Response(response.body, { status: response.status, headers: { "Content-Type": response.headers.get("content-type") || "application/json", "Cache-Control": "no-store" } });
  } catch { return Response.json({ detail: "The operations gateway is unavailable. Check that the services are running, then try again." }, { status: 502 }); }
}
export { proxy as GET, proxy as POST, proxy as PUT, proxy as PATCH, proxy as DELETE };
