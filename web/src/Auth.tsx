import { useState, type FormEvent } from "react";
import {
  Eye,
  EyeOff,
  LockKeyhole,
  Mail,
  SlidersHorizontal,
} from "lucide-react";
import { api, ApiError } from "./api";
import { Brand, ConsoleArt, Field, Problem } from "./ui";
import { errorText, type User, type Org } from "./domain";
export function Auth({
  signed,
  expired,
}: {
  signed: (u: User) => void;
  expired: boolean;
}) {
  const [register, setRegister] = useState(location.hash === "#register"),
    [show, setShow] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const [choices, setChoices] = useState<Org[]>([]);
  const invitation = location.hash.match(/^#join\/([a-zA-Z0-9_-]+)$/)?.[1];
  const workspaceId = location.hash.match(/^#login\/([0-9a-f-]{36})$/i)?.[1];
  async function send(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    if (invitation) data.set("token", invitation);
    if (!data.get("organizationId")) data.delete("organizationId");
    setBusy(true);
    setError("");
    try {
      signed(
        await api<User>(
          invitation ? "/auth/accept-invite" : register ? "/auth/register" : "/auth/login",
          "POST",
          Object.fromEntries(data),
        ),
      );
    } catch (e) {
      setChoices(e instanceof ApiError ? e.organizations || [] : []);
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="auth">
      <section className="auth-main">
        <Brand />
        <div className="auth-form">
          <div className="auth-symbol">
            <SlidersHorizontal size={25} />
          </div>
          <h1>{invitation ? "Join your team" : register ? "Create your workspace" : "Welcome back"}</h1>
          <p>
            {invitation ? "Use the invited email address. If you already have an account, enter your existing password." : register
              ? "Set up your account to manage feature releases."
              : "Sign in to your Switchly workspace."}
          </p>
          {expired && (
            <p role="status">Your session has ended. Sign in again.</p>
          )}
          <form key={String(register)} onSubmit={send}>
            {(register || invitation) && (
              <>
                <Field label="Full name">
                  <input
                    name="fullName"
                    required
                    maxLength={255}
                    autoComplete="name"
                  />
                </Field>
                {!invitation && <Field label="Organization name (optional)">
                  <input
                    name="organizationName"
                    maxLength={255}
                    autoComplete="organization"
                  />
                </Field>}
              </>
            )}
            {!register && !invitation && (choices.length ? <Field label="Select organization">
              <select required name="organizationId" defaultValue=""><option value="" disabled>Choose your workspace</option>
                {choices.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}</select>
            </Field> : <Field label="Organization ID" hint="Optional. You can also choose your workspace after entering your password.">
              <input name="organizationId" defaultValue={workspaceId} placeholder="Workspace ID from your admin" pattern="[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}" />
            </Field>)}
            <Field label="Email address">
              <span className="input-icon">
                <Mail size={17} />
                <input
                  name="email"
                  type="email"
                  placeholder="you@company.com"
                  maxLength={255}
                  autoComplete="email"
                  required
                />
              </span>
            </Field>
            <Field label="Password">
              <span className="input-icon">
                <LockKeyhole size={17} />
                <input
                  aria-label="Password"
                  name="password"
                  type={show ? "text" : "password"}
                  autoComplete={register ? "new-password" : "current-password"}
                  minLength={register || invitation ? 8 : 1}
                  maxLength={register ? 100 : undefined}
                  placeholder={
                    register ? "At least 8 characters" : "Enter your password"
                  }
                  required
                />
                <button
                  type="button"
                  className="icon"
                  aria-label={show ? "Hide password" : "Show password"}
                  onClick={() => setShow(!show)}
                >
                  {show ? <EyeOff size={17} /> : <Eye size={17} />}
                </button>
              </span>
            </Field>
            {error && <Problem error={error} />}
            <button className="primary wide" disabled={busy}>
              {busy ? "Please wait…" : invitation ? "Join organization" : register ? "Create account" : "Sign in"}
            </button>
          </form>
          {!invitation && <p className="auth-link">
            {register ? "Already have a workspace?" : "New to Switchly?"}{" "}
            <button
              className="text-btn"
              disabled={busy}
              onClick={() => {
                setChoices([]);
                setRegister(!register);
                setError("");
                setShow(false);
              }}
            >
              {register ? "Sign in" : "Create an account"}
            </button>
          </p>}
        </div>
      </section>
      <section className="auth-art">
        <ConsoleArt />
        <div className="art-caption">
          <span className="overline">Release console</span>
          <h2>
            Release at
            <br />
            your own pace.
          </h2>
          <div className="env-legend">
            <span>Development</span>
            <span>Staging</span>
            <span>Production</span>
          </div>
        </div>
      </section>
    </main>
  );
}
