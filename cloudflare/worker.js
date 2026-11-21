// Servidor mínimo (Cloudflare Worker) entre o app e a Pluggy.
// Guarda o Client Secret, cria o token do widget e repassa SOMENTE os dados do próprio usuário.
// Não grava nada: não há banco de dados aqui.

const PLUGGY = "https://api.pluggy.ai";
const OAUTH_REDIRECT = "meubanco://pluggy";
let apiKeyCache = { key: null, exp: 0 };
const authCache = new Map();   // token Google -> { uid, exp }
const hits = new Map();        // uid -> { n, reset }

const json = (obj, status = 200) => new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json" } });
const err = (status, message) => json({ error: message }, status);

async function pluggyKey(env, f) {
  if (apiKeyCache.key && Date.now() < apiKeyCache.exp) return apiKeyCache.key;
  const r = await f(PLUGGY + "/auth", { method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ clientId: env.PLUGGY_CLIENT_ID, clientSecret: env.PLUGGY_CLIENT_SECRET }) });
  if (!r.ok) throw new Error("pluggy-auth-" + r.status);
  const { apiKey } = await r.json();
  apiKeyCache = { key: apiKey, exp: Date.now() + 90 * 60 * 1000 }; // a chave dura 2h
  return apiKey;
}

async function pluggy(env, f, method, path, body) {
  const key = await pluggyKey(env, f);
  const r = await f(PLUGGY + path, { method, headers: { "X-API-KEY": key, "content-type": "application/json", accept: "application/json" }, body: body ? JSON.stringify(body) : undefined });
  const text = await r.text();
  let data = null; try { data = JSON.parse(text); } catch { /* corpo vazio */ }
  return { status: r.status, data, text };
}

/** Confere o token Google do usuário e devolve um identificador estável dele. */
async function whoIs(request, env, f) {
  const h = request.headers.get("authorization") || "";
  const token = h.startsWith("Bearer ") ? h.slice(7).trim() : "";
  if (!token) return null;
  const c = authCache.get(token);
  if (c && c.exp > Date.now()) return c.uid;
  if (env.GOOGLE_ANDROID_CLIENT_ID) { // garante que o token foi emitido para o NOSSO app
    const t = await f("https://oauth2.googleapis.com/tokeninfo?access_token=" + encodeURIComponent(token));
    if (!t.ok) return null;
    const info = await t.json();
    if (info.azp !== env.GOOGLE_ANDROID_CLIENT_ID && info.aud !== env.GOOGLE_ANDROID_CLIENT_ID) return null;
  }
  const r = await f("https://www.googleapis.com/drive/v3/about?fields=user(permissionId)", { headers: { authorization: "Bearer " + token } });
  if (!r.ok) return null;
  const id = (await r.json())?.user?.permissionId;
  if (!id) return null;
  const uid = "g-" + id;
  authCache.set(token, { uid, exp: Date.now() + 5 * 60 * 1000 });
  if (authCache.size > 500) authCache.clear();
  return uid;
}

function limited(uid) {
  const now = Date.now(); const h = hits.get(uid);
  if (!h || h.reset < now) { hits.set(uid, { n: 1, reset: now + 60000 }); return false; }
  h.n++; return h.n > 120;
}

/** A conexão (item) só é liberada se pertencer ao usuário que pediu. Falha fechada. */
async function ownsItem(env, f, uid, itemId) {
  if (!/^[0-9a-f-]{36}$/i.test(itemId || "")) return false;
  const r = await pluggy(env, f, "GET", "/items/" + itemId);
  return r.status === 200 && r.data && r.data.clientUserId === uid;
}

export async function handle(request, env, f = fetch) {
  const url = new URL(request.url);
  if (url.pathname === "/" ) return json({ ok: true, app: "meu-banco-api" });
  const uid = await whoIs(request, env, f);
  if (!uid) return err(401, "Entre com sua conta Google de novo.");
  if (limited(uid)) return err(429, "Muitas consultas. Tente em instantes.");

  // 1) token do widget de conexão
  if (url.pathname === "/connect-token" && request.method === "POST") {
    let itemId; try { itemId = (await request.json()).itemId; } catch { /* sem corpo */ }
    if (itemId && !(await ownsItem(env, f, uid, itemId))) return err(403, "Conexão não é sua.");
    const body = { options: { clientUserId: uid, oauthRedirectUri: OAUTH_REDIRECT, avoidDuplicates: true } };
    if (itemId) body.itemId = itemId;
    const r = await pluggy(env, f, "POST", "/connect_token", body);
    if (r.status !== 200 || !r.data?.accessToken) return err(502, "A Pluggy não liberou a conexão agora.");
    return json({ accessToken: r.data.accessToken });
  }

  // 2) apagar uma conexão (LGPD: o usuário pode remover seus dados)
  const del = url.pathname.match(/^\/p\/items\/([^/]+)$/);
  if (del && request.method === "DELETE") {
    if (!(await ownsItem(env, f, uid, del[1]))) return err(403, "Conexão não é sua.");
    const r = await pluggy(env, f, "DELETE", "/items/" + del[1]);
    return json({ ok: r.status < 300 }, r.status < 300 ? 200 : 502);
  }

  // 3) leitura de dados (somente GET), apenas rotas conhecidas
  if (request.method === "GET" && url.pathname.startsWith("/p/")) {
    const path = url.pathname.slice(2);
    let itemId = null;
    if (/^\/items\/[^/]+$/.test(path)) itemId = path.split("/")[2];
    else if (path === "/accounts" || path === "/investments") itemId = url.searchParams.get("itemId");
    else if (path === "/v2/transactions") {
      const acc = url.searchParams.get("accountId");
      if (!/^[0-9a-f-]{36}$/i.test(acc || "")) return err(400, "accountId inválido");
      const a = await pluggy(env, f, "GET", "/accounts/" + acc);
      itemId = a.data?.itemId;
    } else return err(404, "Rota não permitida");
    if (!(await ownsItem(env, f, uid, itemId))) return err(403, "Conexão não é sua.");
    const r = await pluggy(env, f, "GET", path + url.search);
    return new Response(r.text, { status: r.status, headers: { "content-type": "application/json" } });
  }
  return err(404, "Rota não encontrada");
}

export default { fetch: (request, env) => handle(request, env) };
