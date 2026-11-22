import { draftFlag } from "./draft.mjs";
import { randomBytes } from "node:crypto";

const id = "[0-9a-fA-F-]{36}";
const key = "[a-z0-9-]{1,255}";
const routes = [
  ["GET POST", "orgs"],
  ["GET POST", "orgs/" + id + "/members/invitations"],
  ["POST", "orgs/" + id + "/members/invitations/cancel"],
  ["PUT", "orgs/" + id + "/members/" + id + "/role"],
  ["DELETE", "orgs/" + id + "/members/" + id],
  ["POST", "projects/" + id + "/flag-drafts"],
  ["POST", "environments/" + id + "/flags/" + id + "/preview"],
  ["GET", "auth/me"],
  ["GET", "orgs/" + id + "/members"],
  ["GET POST", "orgs/" + id + "/projects"],
  ["GET POST", "projects/" + id + "/(flags|environments|segments|sdk-keys)"],
  ["GET PUT DELETE", "flags/" + id],
  ["GET PUT DELETE", "environments/" + id],
  ["GET", "environments/" + id + "/flags"],
  ["GET", "environments/" + id + "/flags/" + id],
  ["PUT", "environments/" + id + "/flags/" + id + "/(state|value)"],
  ["GET PUT", "environments/" + id + "/flags/" + id + "/(targeting|rollout)"],
  ["DELETE", "projects/" + id + "/sdk-keys/" + id],
  ["GET PUT DELETE", "projects/" + id + "/segments/" + key],
  ["POST", "projects/" + id + "/segments/" + key + "/evaluate"],
].map(([methods, path]) => [methods.split(" "), new RegExp("^" + path + "$")]);

function fail(res, status, code, message) {
  res.writeHead(status, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ error: { code, message } }));
}
async function bodyOf(req) {
  const parts = [];
  let size = 0;
  for await (const part of req) {
    size += part.length;
    if (size > 4 * 1024 * 1024) throw new RangeError("Request is too large");
    parts.push(part);
  }
  return parts.length ? Buffer.concat(parts) : undefined;
}

export function createGateway({ origin, secure = true, fetcher = fetch }) {
  const backend = new URL(origin);
  if (!["https:", "http:"].includes(backend.protocol))
    throw new Error("Invalid API_ORIGIN");
  const sessions = new Map();
  const lifetime = 7 * 24 * 3600 * 1000;
  const cookie = "switchly_session";
  const expired = () =>
    new Response(
      '{"error":{"code":"UNAUTHORIZED","message":"Your session has ended. Sign in again."}}',
      { status: 401 },
    );
  const sweep = setInterval(() => {
    for (const [sid, s] of sessions)
      if (s.until <= Date.now()) sessions.delete(sid);
  }, 60_000).unref();

  function call(path, method, body, token) {
    const headers = { "Content-Type": "application/json" };
    if (token) headers.Authorization = "Bearer " + token;
    return fetcher(new URL("/api/v1/" + path, backend), {
      method,
      headers,
      body,
      redirect: "error",
      signal: AbortSignal.timeout(20_000),
    });
  }
  // Share rotation across simultaneous requests and tabs using the same opaque cookie.
  async function renew(s, used) {
    if (s.access !== used) return null;
    if (!s.pending) {
      s.pending = (async () => {
        const r = await call(
          "auth/refresh",
          "POST",
          JSON.stringify({ refreshToken: s.refresh }),
        );
        if (!r.ok) return { status: r.status, body: await r.text() };
        const result = await r.json();
        s.access = result.accessToken;
        s.refresh = result.refreshToken;
        return null;
      })().finally(() => {
        s.pending = undefined;
      });
    }
    return s.pending;
  }
  async function send(res, reply) {
    if (reply.headers.has("retry-after"))
      res.setHeader("Retry-After", reply.headers.get("retry-after"));
    res.writeHead(reply.status, { "Content-Type": "application/json" });
    res.end(await reply.text());
  }
  async function handle(req, res) {
    res.setHeader("Cache-Control", "no-store");
    res.setHeader("X-Content-Type-Options", "nosniff");
    const method = req.method;
    const path = (req.url || "").slice("/api/v1/".length);
    const sid = (req.headers.cookie || "")
      .split(";")
      .map((x) => x.trim())
      .find((x) => x.startsWith(cookie + "="))
      ?.slice(cookie.length + 1);
    const session = sessions.get(sid);
    const setCookie = (value, age) =>
      res.setHeader(
        "Set-Cookie",
        cookie +
          "=" +
          value +
          "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" +
          age +
          (secure ? "; Secure" : ""),
      );
    if (
      method !== "GET" &&
      (req.headers["x-switchly-request"] !== "console" ||
        req.headers["sec-fetch-site"] === "cross-site")
    ) {
      return fail(
        res,
        403,
        "FORBIDDEN",
        "A same-origin console request is required.",
      );
    }
    try {
      if (
        method === "POST" &&
        ["auth/login", "auth/register", "auth/accept-invite"].includes(path)
      ) {
        const reply = await call(path, method, await bodyOf(req));
        if (!reply.ok) return send(res, reply);
        const data = await reply.json();
        const next = randomBytes(32).toString("hex");
        sessions.delete(sid);
        sessions.set(next, {
          access: data.accessToken,
          refresh: data.refreshToken,
          until: Date.now() + lifetime,
        });
        setCookie(next, lifetime / 1000);
        res.writeHead(reply.status, { "Content-Type": "application/json" });
        return res.end(JSON.stringify(data.user));
      }
      const logout = method === "POST" && path === "auth/logout";
      if (logout) setCookie("", 0);
      if (
        !logout &&
        !routes.some(
          ([methods, match]) => methods.includes(method) && match.test(path),
        )
      ) {
        return fail(res, 404, "NOT_FOUND", "Unknown console operation.");
      }
      if (!session || session.until <= Date.now()) {
        sessions.delete(sid);
        setCookie("", 0);
        return send(res, expired());
      }
      const body = method === "GET" || logout ? undefined : await bodyOf(req);
      async function authorized() {
        const used = session.access;
        const isDraft = path.endsWith("/flag-drafts");
        const invoke = () => isDraft
          ? call(path.slice(0, -"/flag-drafts".length), "GET", undefined, session.access)
          :
          call(
            path,
            method,
            logout ? JSON.stringify({ refreshToken: session.refresh }) : body,
            session.access,
          );
        let reply = await invoke();
        if (reply.status === 401) {
          await reply.arrayBuffer();
          const error = await renew(session, used);
          reply = error
            ? new Response(error.body, { status: error.status })
            : await invoke();
        }
        if (isDraft && reply.ok) {
          await reply.arrayBuffer();
          return draftFlag(body);
        }
        return reply;
      }
      try {
        const reply = await authorized();
        if (reply.status === 401) {
          sessions.delete(sid);
          setCookie("", 0);
        }
        return await send(res, reply);
      } finally {
        if (logout) sessions.delete(sid);
      }
    } catch (e) {
      if (!res.headersSent)
        fail(
          res,
          e instanceof RangeError ? 413 : 502,
          "REQUEST_FAILED",
          e instanceof RangeError
            ? "Request is too large."
            : "Switchly API is unavailable. Try again.",
        );
    }
  }
  return {
    handle,
    close: () => {
      clearInterval(sweep);
      sessions.clear();
    },
  };
}
