import http from "node:http";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { createGateway } from "./gateway.mjs";

export async function start({ development = false } = {}) {
  const gateway = createGateway({
    origin: process.env.API_ORIGIN || "http://127.0.0.1:8080",
    secure:
      (process.env.COOKIE_SECURE !== "false" &&
        process.env.NODE_ENV === "production") ||
      process.env.COOKIE_SECURE === "true",
  });
  const server = http.createServer();
  const vite = development
    ? await (
        await import("vite")
      ).createServer({
        server: { middlewareMode: true, hmr: { server } },
        appType: "spa",
      })
    : null;
  server.on("request", async (req, res) => {
    if (req.url?.startsWith("/api/v1/")) return gateway.handle(req, res);
    if (vite) return vite.middlewares(req, res);
    const path = (req.url || "/").split("?")[0];
    if (
      req.method !== "GET" ||
      !(
        path === "/" ||
        path === "/switchly.svg" ||
        path === "/index.html" ||
        /^\/assets\/[\w.-]+$/.test(path)
      )
    ) {
      res.writeHead(404);
      return res.end();
    }
    const file = path === "/" ? "index.html" : path.slice(1);
    try {
      const content = await readFile(
        new URL("../dist/" + file, import.meta.url),
      );
      const types = {
        js: "text/javascript",
        css: "text/css",
        html: "text/html",
        svg: "image/svg+xml",
        woff2: "font/woff2",
      };
      res.writeHead(200, {
        "Content-Type":
          types[file.split(".").pop()] || "application/octet-stream",
        "X-Content-Type-Options": "nosniff",
        "Content-Security-Policy":
          "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; object-src 'none'; base-uri 'none'; form-action 'self'",
        "Referrer-Policy": "no-referrer",
        "Cache-Control": path.startsWith("/assets/")
          ? "public, max-age=31536000, immutable"
          : "no-cache",
      });
      res.end(content);
    } catch {
      res.writeHead(404);
      res.end();
    }
  });
  server.on("close", () => {
    gateway.close();
    vite?.close();
  });
  server.listen(
    Number(process.env.PORT || 3000),
    process.env.HOST || "127.0.0.1",
    () => {
      console.log(
        "Switchly console listening at http://" +
          server.address().address +
          ":" +
          server.address().port,
      );
    },
  );
  return server;
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const server = await start();
  const drain = () => {
    server.close();
    console.log("Switchly console draining connections");
  };
  process.once("SIGTERM", drain);
  process.once("SIGINT", drain);
}
