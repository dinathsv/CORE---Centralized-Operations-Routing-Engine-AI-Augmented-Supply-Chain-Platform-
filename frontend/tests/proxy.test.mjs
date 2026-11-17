import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { createServer } from "node:http";
import { setTimeout as delay } from "node:timers/promises";
import { after, before, test } from "node:test";

// Exercise the compiled Next route over HTTP. The gateway stub observes the
// actual boundary: paths, credentials, request bodies, and response statuses.
const authorization = `Basic ${Buffer.from("operator:test-password").toString("base64")}`;
const requests = [];
let upstream;
let frontend;
let base;
let logs = "";

before(async () => {
  upstream = createServer(async (request, response) => {
    let body = "";
    for await (const chunk of request) body += chunk;
    requests.push({ url: request.url, method: request.method, authorization: request.headers.authorization, body });
    if (request.url === "/api/inventory/404") {
      response.writeHead(404, { "Content-Type": "application/problem+json" });
      response.end(JSON.stringify({ detail: "Item not found" }));
    } else if (request.method === "DELETE") {
      response.writeHead(204); response.end();
    } else {
      response.writeHead(request.method === "POST" ? 201 : 200, { "Content-Type": "application/json" });
      response.end(JSON.stringify({ ok: true }));
    }
  });
  upstream.listen(0, "127.0.0.1");
  await once(upstream, "listening");
  frontend = spawn(process.execPath, ["node_modules/next/dist/bin/next", "start", "--hostname", "127.0.0.1", "--port", "0"], {
    cwd: process.cwd(),
    env: { ...process.env, GATEWAY_URL: `http://127.0.0.1:${upstream.address().port}`, NEXT_TELEMETRY_DISABLED: "1" },
    stdio: ["ignore", "pipe", "pipe"],
  });
  frontend.stdout.on("data", (data) => { logs += data; });
  frontend.stderr.on("data", (data) => { logs += data; });
  for (let attempt = 0; attempt < 100; attempt++) {
    const match = logs.match(/http:\/\/127\.0\.0\.1:(\d+)/);
    if (match) {
      base = match[0];
      try { if ((await fetch(base)).ok) return; } catch { /* server is still starting */ }
    }
    if (frontend.exitCode !== null) throw new Error(`Next server exited: ${logs}`);
    await delay(100);
  }
  throw new Error(`Next server did not become ready: ${logs}`);
});

after(async () => {
  if (frontend && frontend.exitCode === null) {
    const exited = once(frontend, "exit");
    frontend.kill("SIGTERM");
    await exited;
  }
  if (upstream?.listening) await new Promise((resolve, reject) => upstream.close((error) => error ? reject(error) : resolve()));
});

test("workspace renders its own title and sign-in screen", async () => {
  const response = await fetch(base);
  const html = await response.text();
  assert.equal(response.status, 200);
  assert.match(html, /CORE — Operations Workspace/);
  assert.match(html, /Welcome to CORE/);
});

test("unknown resources and missing credentials never reach the gateway", async () => {
  const initial = requests.length;
  assert.equal((await fetch(`${base}/api/private`, { headers: { Authorization: authorization } })).status, 404);
  assert.equal((await fetch(`${base}/api/inventory`)).status, 401);
  const denied = await fetch(`${base}/api/orders`, { method: "PUT", headers: { Authorization: authorization } });
  assert.equal(denied.status, 405);
  assert.equal(denied.headers.get("allow"), "GET, POST");
  assert.equal(requests.length, initial);
});

test("forwards the supported inventory path and operator credentials", async () => {
  const response = await fetch(`${base}/api/inventory`, { headers: { Authorization: authorization } });
  assert.equal(response.status, 200);
  assert.equal(response.headers.get("cache-control"), "no-store");
  assert.deepEqual(requests.at(-1), { url: "/api/inventory", method: "GET", authorization, body: "" });
});

test("preserves JSON write bodies and created responses", async () => {
  const body = JSON.stringify({ customerName: "Acme", sku: "SKU-123", quantity: 2 });
  const response = await fetch(`${base}/api/orders`, { method: "POST", headers: { Authorization: authorization, "Content-Type": "application/json" }, body });
  assert.equal(response.status, 201);
  assert.deepEqual(requests.at(-1), { url: "/api/orders", method: "POST", authorization, body });
});

test("preserves status transition query parameters and empty delete responses", async () => {
  await fetch(`${base}/api/orders/1/status?status=CONFIRMED`, { method: "PATCH", headers: { Authorization: authorization } });
  assert.equal(requests.at(-1).url, "/api/orders/1/status?status=CONFIRMED");
  assert.equal(requests.at(-1).method, "PATCH");
  const response = await fetch(`${base}/api/inventory/1`, { method: "DELETE", headers: { Authorization: authorization } });
  assert.equal(response.status, 204);
  assert.equal(await response.text(), "");
});

test("preserves downstream problem details", async () => {
  const response = await fetch(`${base}/api/inventory/404`, { headers: { Authorization: authorization } });
  assert.equal(response.status, 404);
  assert.equal(response.headers.get("content-type"), "application/problem+json");
  assert.deepEqual(await response.json(), { detail: "Item not found" });
});

test("forwards document ingestion and AI queries", async () => {
  for (const resource of ["documents", "query"]) {
    const body = JSON.stringify(resource === "query" ? { query: "Restock policy?", collection: "operations" } : { collection: "operations", documents: [{ id: "policy", source: "Handbook", text: "Reorder at ten units." }] });
    const response = await fetch(`${base}/api/ai/${resource}`, { method: "POST", headers: { Authorization: authorization, "Content-Type": "application/json" }, body });
    assert.equal(response.status, 201);
    assert.equal(requests.at(-1).url, `/api/ai/${resource}`);
    assert.equal(requests.at(-1).body, body);
  }
});

test("reports an unavailable gateway without exposing connection details", async () => {
  await new Promise((resolve, reject) => upstream.close((error) => error ? reject(error) : resolve()));
  const response = await fetch(`${base}/api/inventory`, { headers: { Authorization: authorization } });
  assert.equal(response.status, 502);
  assert.match((await response.json()).detail, /gateway is unavailable/);
});
