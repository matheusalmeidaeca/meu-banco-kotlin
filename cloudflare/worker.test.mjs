import { handle } from "./worker.js";
import assert from "node:assert/strict";

const ME = "11111111-1111-1111-1111-111111111111", OTHER = "22222222-2222-2222-2222-222222222222", ACC = "33333333-3333-3333-3333-333333333333";
const calls = [];
const f = async (url, opts = {}) => {
  calls.push((opts.method || "GET") + " " + url);
  const J = (o, s = 200) => new Response(JSON.stringify(o), { status: s });
  if (url.includes("tokeninfo")) return J({ azp: "CLIENT" });
  if (url.includes("drive/v3/about")) return opts.headers.authorization === "Bearer GOOD" ? J({ user: { permissionId: "P1" } }) : J({}, 401);
  if (url.endsWith("/auth")) return J({ apiKey: "KEY" });
  assert.equal(opts.headers["X-API-KEY"], "KEY");
  if (url.endsWith("/connect_token")) return J({ accessToken: "CT" });
  if (url.endsWith("/items") && opts.method === "POST") { const b = JSON.parse(opts.body); return b.connectorId === 999 ? J({ message: "connector not found" }, 400) : J({ id: ME, status: "CREATED", echo: b }, 201); }
  if (url.endsWith("/items/" + ME)) return J({ id: ME, clientUserId: "g-P1", status: "WAITING_USER_ACTION", parameter: { type: "oauth", data: "https://meu.pluggy.ai/oauth?x=1" } });
  if (url.endsWith("/items/" + OTHER)) return J({ id: OTHER, clientUserId: "g-OUTRO" });
  if (url.endsWith("/accounts/" + ACC)) return J({ id: ACC, itemId: ME });
  if (url.includes("/accounts?itemId=") || url.includes("/investments?") || url.includes("/v2/transactions?")) return J({ results: [] });
  return J({}, 404);
};
const env = { PLUGGY_CLIENT_ID: "a", PLUGGY_CLIENT_SECRET: "b", GOOGLE_ANDROID_CLIENT_ID: "CLIENT" };
const req = (m, p, tok = "GOOD", body) => new Request("https://x" + p, { method: m, headers: tok ? { authorization: "Bearer " + tok } : {}, body: body ? JSON.stringify(body) : undefined });

assert.equal((await handle(req("POST", "/connect-token", null), env, f)).status, 401);
assert.equal((await handle(req("POST", "/connect-token", "RUIM"), env, f)).status, 401);
let r = await handle(req("POST", "/connect-token"), env, f);
assert.equal(r.status, 200); assert.equal((await r.json()).accessToken, "CT");
assert.ok(calls.some(c => c.includes("/connect_token")));
assert.equal((await handle(req("GET", `/p/accounts?itemId=${ME}`), env, f)).status, 200);
assert.equal((await handle(req("GET", `/p/accounts?itemId=${OTHER}`), env, f)).status, 403);        // item de outra pessoa
assert.equal((await handle(req("GET", `/p/v2/transactions?accountId=${ACC}&dateFrom=2026-01-01`), env, f)).status, 200);
assert.equal((await handle(req("GET", `/p/investments?itemId=${ME}&pageSize=500`), env, f)).status, 200);
assert.equal((await handle(req("GET", `/p/webhooks`), env, f)).status, 404);                        // rota fora da lista
assert.equal((await handle(req("POST", `/p/accounts?itemId=${ME}`), env, f)).status, 404);          // só GET
assert.equal((await handle(req("DELETE", `/p/items/${OTHER}`), env, f)).status, 403);
assert.equal((await handle(req("DELETE", `/p/items/${ME}`), env, f)).status, 200);
assert.equal((await handle(req("GET", `/p/accounts?itemId=nao-uuid`), env, f)).status, 403);
// token de outro app (audiência errada) é recusado
const f2 = async (u, o) => u.includes("tokeninfo") ? new Response(JSON.stringify({ azp: "OUTRO" })) : f(u, o);
assert.equal((await handle(req("POST", "/connect-token", "GOOD2"), env, f2)).status, 401);
// conexão direta (OAuth sem widget)
r = await handle(req("POST", "/oauth-item", "GOOD", {}), env, f);
assert.equal(r.status, 200); assert.equal((await r.json()).itemId, ME);
assert.ok(calls.some(c => c === "POST https://api.pluggy.ai/items"));
r = await handle(req("POST", "/oauth-item", "GOOD", { connectorId: 999 }), env, f);
assert.equal(r.status, 502); assert.ok((await r.json()).error.includes("connector not found"));   // erro real aparece
assert.equal((await handle(req("POST", "/oauth-item", null), env, f)).status, 401);
r = await handle(req("GET", `/oauth-item/${ME}`), env, f);
let oj = await r.json(); assert.equal(r.status, 200); assert.equal(oj.oauthUrl, "https://meu.pluggy.ai/oauth?x=1");
assert.equal((await handle(req("GET", `/oauth-item/${OTHER}`), env, f)).status, 403);
// página do widget (sem login Google; o token da Pluggy é curto e só abre o widget)
const pg = await handle(new Request("https://x.test/connect?t=" + "a".repeat(40) + "&s=1"), env, f);
assert.equal(pg.status, 200);
const html = await pg.text();
assert.ok(html.includes("/pluggy-connect/latest/pluggy-connect.js") && html.includes("includeSandbox: true") && html.includes("admoney"));
assert.equal((await handle(new Request("https://x.test/connect?t=curto"), env, f)).status, 400);
console.log("worker: todos os testes passaram");
