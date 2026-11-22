import test from "node:test";
import assert from "node:assert/strict";
import { Readable } from "node:stream";
import { createGateway } from "../server/gateway.mjs";
function setup(t, backend) {
  const gateway = createGateway({ origin: "http://backend", fetcher: backend });
  t.after(gateway.close);
  return async (path, method = "GET", cookie = "", body, headers = {}) => {
    const req = Object.assign(
      Readable.from(
        body === undefined ? [] : [Buffer.from(JSON.stringify(body))],
      ),
      {
        url: "/api/v1/" + path,
        method,
        headers: { cookie, "x-switchly-request": "console", ...headers },
      },
    );
    const res = {
      headers: {},
      setHeader(k, v) {
        this.headers[k] = v;
      },
      writeHead(status, headers) {
        this.status = status;
        Object.assign(this.headers, headers);
      },
      end(value) {
        this.body = value;
      },
    };
    await gateway.handle(req, res);
    return res;
  };
}
const tokens = (n = 1) =>
  Response.json({
    user: { id: "u", email: "u@example.test" },
    accessToken: "access-" + n,
    refreshToken: "refresh-" + n,
  });
async function login(send) {
  const r = await send("auth/login", "POST", "", {
    email: "u@example.test",
    password: "test",
  });
  return r.headers["Set-Cookie"].split(";")[0];
}

test("sign-in exposes user and an HttpOnly cookie, retaining tokens on the server", async (t) => {
  const send = setup(t, async () => tokens());
  const r = await send("auth/login", "POST");
  assert.deepEqual(JSON.parse(r.body), { id: "u", email: "u@example.test" });
  assert.match(r.headers["Set-Cookie"], /HttpOnly; SameSite=Strict;.*Secure/);
  assert.equal(r.headers["Cache-Control"], "no-store");
  assert.equal((await send("auth/me")).status, 401);
});
test("simultaneous expired requests rotate once and retry with the replacement JWT", async (t) => {
  let rotations = 0;
  const send = setup(t, async (url, options) => {
    if (url.pathname.endsWith("/login")) return tokens();
    if (url.pathname.endsWith("/refresh")) {
      rotations++;
      assert.equal(JSON.parse(options.body).refreshToken, "refresh-1");
      await new Promise((r) => setTimeout(r, 15));
      return tokens(2);
    }
    return options.headers.Authorization === "Bearer access-2"
      ? Response.json([])
      : new Response(null, { status: 401 });
  });
  const cookie = await login(send);
  const responses = await Promise.all(
    Array.from({ length: 8 }, () => send("orgs", "GET", cookie)),
  );
  assert.ok(responses.every((r) => r.status === 200));
  assert.equal(rotations, 1);
});
test("revoked refresh token ends the session", async (t) => {
  let backendCalls = 0;
  const send = setup(t, async (url) => {
    backendCalls++;
    return url.pathname.endsWith("/login")
      ? tokens()
      : new Response(null, { status: 401 });
  });
  const sid = await login(send);
  assert.equal((await send("auth/me", "GET", sid)).status, 401);
  const before = backendCalls;
  assert.equal((await send("orgs", "GET", sid)).status, 401);
  assert.equal(backendCalls, before);
});
test("transient refresh failure allows a later retry", async (t) => {
  let attempts = 0;
  const send = setup(t, async (url, o) => {
    if (url.pathname.endsWith("/login")) return tokens();
    if (url.pathname.endsWith("/refresh"))
      return ++attempts === 1 ? new Response(null, { status: 503 }) : tokens(2);
    return new Response(null, {
      status: o.headers.Authorization === "Bearer access-2" ? 200 : 401,
    });
  });
  const sid = await login(send);
  assert.equal((await send("orgs", "GET", sid)).status, 503);
  assert.equal((await send("orgs", "GET", sid)).status, 200);
});
test("logout revokes with current refresh token and expires the opaque cookie", async (t) => {
  const send = setup(t, async (url, o) => {
    if (url.pathname.endsWith("/login")) return tokens();
    assert.equal(JSON.parse(o.body).refreshToken, "refresh-1");
    return new Response(null, { status: 204 });
  });
  const sid = await login(send),
    r = await send("auth/logout", "POST", sid);
  assert.equal(r.status, 204);
  assert.match(r.headers["Set-Cookie"], /Max-Age=0/);
  assert.equal((await send("orgs", "GET", sid)).status, 401);
});
test("rejects cross-site writes, unknown routes and browser SDK evaluation", async (t) => {
  let calls = 0;
  const send = setup(t, async () => {
    calls++;
    return tokens();
  });
  assert.equal(
    (
      await send(
        "auth/login",
        "POST",
        "",
        {},
        { "sec-fetch-site": "cross-site" },
      )
    ).status,
    403,
  );
  assert.equal(
    (await send("orgs", "POST", "", {}, { "x-switchly-request": "" })).status,
    403,
  );
  assert.equal((await send("evaluate", "POST")).status, 404);
  assert.equal((await send("auth/refresh", "POST")).status, 404);
  assert.equal(calls, 0);
});
test("forwards backend permission failures and rate-limit timing", async (t) => {
  const send = setup(t, async (url) =>
    url.pathname.endsWith("/login")
      ? tokens()
      : new Response(
          '{"error":{"code":"RATE_LIMIT_EXCEEDED","message":"Slow down"}}',
          { status: 429, headers: { "Retry-After": "8" } },
        ),
  );
  const r = await send("orgs", "GET", await login(send));
  assert.equal(r.status, 429);
  assert.equal(r.headers["Retry-After"], "8");
});
