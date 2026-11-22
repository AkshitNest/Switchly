import { decode, encode, type Org } from "./domain";
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly organizations?: Org[],
  ) {
    super(message);
  }
}
export async function api<T>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  const res = await fetch("/api/v1" + path, {
    method,
    credentials: "same-origin",
    cache: "no-store",
    headers: {
      "Content-Type": "application/json",
      "X-Switchly-Request": "console",
    },
    body: body === undefined ? undefined : encode(body),
  });
  const text = await res.text();
  if (!res.ok) {
    let message = "Request failed (" + res.status + "). Please try again.";
    let organizations: Org[] | undefined;
    try {
      const result = JSON.parse(text);
      message = result.error.message || message;
      if (result.error.code === "WORKSPACE_REQUIRED") organizations = result.organizations;
    } catch {
      /* Non-JSON upstream response. */
    }
    if (
      res.status === 401 &&
      !["/auth/login", "/auth/register", "/auth/accept-invite", "/auth/me"].includes(path)
    )
      window.dispatchEvent(new Event("switchly:expired"));
    throw new ApiError(res.status, message, organizations);
  }
  return (text ? decode(text) : undefined) as T;
}
export const projectPath = (id: string) =>
  "/projects/" + encodeURIComponent(id);
export const configPath = (env: string, flag: string) =>
  "/environments/" +
  encodeURIComponent(env) +
  "/flags/" +
  encodeURIComponent(flag);
