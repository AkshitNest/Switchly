import { useEffect, useRef, useState } from "react";
import {
  Boxes,
  Building2,
  ChevronRight,
  Flag as FlagIcon,
  Folder,
  KeyRound,
  LayoutDashboard,
  Layers3,
  LogOut,
  Menu,
  UsersRound,
  X,
} from "lucide-react";
import { Team } from "./Team";
import { Auth } from "./Auth";
import { api, ApiError, projectPath } from "./api";
import {
  canAdmin,
  canEdit,
  dateText,
  envOrder,
  errorText,
  type Config,
  type Env,
  type Flag,
  type Org,
  type Project,
  type Role,
  type Segment,
  type User,
} from "./domain";
import {
  Add,
  Brand,
  Busy,
  Empty,
  Field,
  FormDialog,
  Heading,
  Problem,
  Select,
  Tag,
  Toast,
  ToastContext,
  field,
  useResource,
  useToast,
} from "./ui";
import { FlagDetail, FlagForm, FlagList } from "./Flags";
import { Environments, Keys, Segments } from "./Resources";

const nav = [
  { id: "overview", title: "Overview", icon: LayoutDashboard },
  { id: "flags", title: "Feature flags", icon: FlagIcon },
  { id: "environments", title: "Environments", icon: Layers3 },
  { id: "segments", title: "Segments", icon: UsersRound },
  { id: "team", title: "Team access", icon: Building2 },
  { id: "keys", title: "SDK keys", icon: KeyRound },
];
const route = () => location.hash.replace(/^#\/?/, "").split("/");
function go(org: string, project = "", page = "overview", flag = "") {
  location.hash = "/" + [org, project, page, ...(flag ? [flag] : [])].join("/");
}
const initials = (s: string) =>
  s
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((x) => x[0])
    .join("")
    .toUpperCase();
export default function App() {
  const [user, setUser] = useState<User | null | undefined>(),
    [error, setError] = useState(""),
    [attempt, setAttempt] = useState(0),
    [expired, setExpired] = useState(false);
  const [authLink, setAuthLink] = useState(() => /^#(join|login)\//.test(location.hash));
  useEffect(() => {
    const changed = () => setAuthLink(/^#(join|login)\//.test(location.hash));
    window.addEventListener("hashchange", changed);
    return () => window.removeEventListener("hashchange", changed);
  }, []);
  const [message, setMessage] = useState({ text: "", id: 0 });
  useEffect(() => {
    let alive = true;
    setError("");
    api<User>("/auth/me")
      .then((u) => {
        if (alive) setUser(u);
      })
      .catch((e) => {
        if (!alive) return;
        if (e instanceof ApiError && e.status === 401) setUser(null);
        else setError(errorText(e));
      });
    return () => {
      alive = false;
    };
  }, [attempt]);
  useEffect(() => {
    const end = () => {
      setUser(null);
      setExpired(true);
      setMessage({ id: 0, text: "" });
    };
    window.addEventListener("switchly:expired", end);
    return () => window.removeEventListener("switchly:expired", end);
  }, []);
  useEffect(() => {
    if (!message.text) return;
    const id = setTimeout(() => setMessage({ id: 0, text: "" }), 4500);
    return () => clearTimeout(id);
  }, [message]);
  const notify = (text: string) => setMessage((v) => ({ text, id: v.id + 1 }));
  if (error)
    return (
      <main className="boot">
        <Brand />
        <Problem error={error} retry={() => setAttempt((n) => n + 1)} />
      </main>
    );
  if (user === undefined)
    return (
      <main className="boot">
        <Brand />
        <Busy />
      </main>
    );
  return (
    <ToastContext.Provider value={notify}>
      {user && !authLink ? (
        <Workspace
          key={user.id}
          user={user}
          logout={async () => {
            try {
              await api("/auth/logout", "POST");
            } catch (e) {
              notify(errorText(e));
              if (!(e instanceof ApiError)) return;
            }
            setExpired(false);
            setUser(null);
            location.hash = "";
          }}
        />
      ) : (
        <Auth
          expired={expired}
          signed={(u) => {
            setAuthLink(false);
            history.replaceState(null, "", location.pathname + location.search);
            setExpired(false);
            setUser(u);
          }}
        />
      )}
      {message.text && (
        <Toast
          text={message.text}
          close={() => setMessage({ text: "", id: 0 })}
        />
      )}
    </ToastContext.Provider>
  );
}
function Workspace({
  user,
  logout,
}: {
  user: User;
  logout: () => Promise<void>;
}) {
  const [url, setUrl] = useState(route),
    [mobile, setMobile] = useState(false),
    [modal, setModal] = useState(""),
    [signingOut, setSigningOut] = useState(false);
  useEffect(() => {
    const fn = () => {
      setUrl(route());
      setMobile(false);
      setModal("");
    };
    window.addEventListener("hashchange", fn);
    return () => window.removeEventListener("hashchange", fn);
  }, []);
  const orgs = useResource(user.id, () => api<Org[]>("/orgs"));
  const org = orgs.data?.find((o) => o.id === url[0]) || orgs.data?.[0];
  const scope = useResource(org?.id || "", async () => {
    if (!org) return null;
    const [projects, members] = await Promise.all([
      api<Project[]>("/orgs/" + org.id + "/projects"),
      api<{ userId: string; role: Role }[]>("/orgs/" + org.id + "/members"),
    ]);
    const role = members.find((m) => m.userId === user.id)?.role;
    if (!role) throw Error("Organization membership could not be verified.");
    return { projects, role };
  });
  const project =
    scope.data?.projects.find((p) => p.id === url[1]) ||
    scope.data?.projects[0];
  const role = scope.data?.role || "EMPLOYEE";
  const page = nav.some((n) => n.id === url[2]) ? url[2] : "overview";
  const change = (v: string, f = "") => {
    if (org) go(org.id, project?.id, v, f);
  };
  const links = nav.filter((n) => !["keys", "team"].includes(n.id) || canAdmin(role));
  const opener = useRef<HTMLButtonElement>(null),
    drawer = useRef<HTMLElement>(null);
  useEffect(() => {
    if (!mobile) return;
    const controls = () =>
      Array.from(
        drawer.current?.querySelectorAll<HTMLElement>(
          "button:not(:disabled),select",
        ) || [],
      ).filter((el) => el.offsetParent !== null);
    controls()[0]?.focus();
    const keydown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setMobile(false);
        return;
      }
      if (e.key !== "Tab") return;
      const items = controls(),
        first = items[0],
        last = items.at(-1);
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last?.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first?.focus();
      }
    };
    const wide = matchMedia("(min-width:721px)");
    const resize = () => {
      if (wide.matches) setMobile(false);
    };
    document.addEventListener("keydown", keydown);
    wide.addEventListener("change", resize);
    return () => {
      document.removeEventListener("keydown", keydown);
      wide.removeEventListener("change", resize);
      opener.current?.focus();
    };
  }, [mobile]);
  const stop = async () => {
    setSigningOut(true);
    try {
      await logout();
    } finally {
      setSigningOut(false);
    }
  };
  return (
    <div className="shell">
      <a
        className="skip"
        href="#main"
        onClick={(e) => {
          e.preventDefault();
          document.getElementById("main")?.focus();
        }}
      >
        Skip to content
      </a>
      {mobile && (
        <button
          aria-label="Close navigation"
          className="scrim"
          onClick={() => setMobile(false)}
        />
      )}
      <aside
        ref={drawer}
        id="workspace-nav"
        className={"sidebar " + (mobile ? "open" : "")}
      >
        <div className="side-brand">
          <Brand />
          <button
            className="mobile-only icon"
            aria-label="Close menu"
            onClick={() => setMobile(false)}
          >
            <X size={20} />
          </button>
        </div>
        <div className="side-scope">
          <span className="overline">Workspace</span>
          <div className="scope-select org-name" aria-label="Organization">
            <span className="scope-icon"><Building2 size={18} /></span>
            <strong>{org?.name || "Workspace"}</strong>
          </div>
        </div>
        <span className="overline nav-label">Project console</span>
        <nav>
          {links.map((n) => (
            <button
              key={n.id}
              disabled={!project && n.id !== "team"}
              aria-current={page === n.id ? "page" : undefined}
              className={page === n.id ? "active" : ""}
              onClick={() => {
                change(n.id);
                setMobile(false);
              }}
            >
              <n.icon size={18} />
              {n.title}
            </button>
          ))}
        </nav>
        <div className="identity">
          <span className="avatar">{initials(user.fullName)}</span>
          <div>
            <strong>{user.fullName}</strong>
            <small>{scope.data?.role.toLowerCase() || user.email}</small>
          </div>
          <button
            className="icon"
            aria-label="Sign out"
            disabled={signingOut}
            onClick={stop}
          >
            <LogOut size={17} />
          </button>
        </div>
      </aside>
      <div className="workspace" inert={mobile}>
        <header className="topbar">
          <button
            ref={opener}
            aria-expanded={mobile}
            aria-controls="workspace-nav"
            className="mobile-only icon"
            aria-label="Open menu"
            onClick={() => setMobile(true)}
          >
            <Menu />
          </button>
          <div className="crumb">
            <Boxes size={17} />
            <span>{org?.name || "Workspace"}</span>
            <ChevronRight size={14} />
            <strong>{project?.name || "Projects"}</strong>
          </div>
          <label className="project-select">
            <Folder size={15} />
            <Select
              aria-label="Project"
              value={project?.id || ""}
              onChange={(e) =>
                e.target.value === "new"
                  ? setModal("project")
                  : org && go(org.id, e.target.value)
              }
            >
              {!project && <option value="">Select project</option>}
              {scope.data?.projects.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
              {canAdmin(role) && <option value="new">+ New project</option>}
            </Select>
          </label>
        </header>
        <main id="main" tabIndex={-1}>
          {orgs.error ? (
            <Problem error={orgs.error} retry={orgs.reload} />
          ) : scope.error ? (
            <Problem error={scope.error} retry={scope.reload} />
          ) : !orgs.data || (org && !scope.data) ? (
            <Busy />
          ) : !org ? (
            <Empty title="Workspace unavailable">Sign in again with your organization ID.</Empty>
          ) : page === "team" && canAdmin(role) ? (
            <Team org={org} projects={scope.data?.projects || []} userId={user.id} changed={scope.reload} />
          ) : !project ? (
            <>
              <Heading title="Projects" sub={"Projects in " + org.name} />
              <div className="panel">
                <Empty
                  title={canAdmin(role) ? "Your first project starts here" : "Waiting for a project assignment"}
                  action={
                    canAdmin(role) && (
                      <Add onClick={() => setModal("project")}>New project</Add>
                    )
                  }
                >
                  {canAdmin(role)
                    ? "Create a project to provision development, staging, and production."
                    : "Ask an administrator to assign your project in Team access."}
                </Empty>
              </div>
            </>
          ) : (
            <ProjectView
              key={project.id}
              {...{ project, role, page, change }}
              flagId={url[3]}
            />
          )}
        </main>
      </div>
      {modal === "project" && org && canAdmin(role) && (
        <FormDialog
          title="New project"
          submit="Create project"
          close={() => setModal("")}
          onSave={async (d) => {
            const key = field(d, "key");
            const n = await api<Project>(
              "/orgs/" + org.id + "/projects",
              "POST",
              { name: field(d, "name"), ...(key ? { key } : {}) },
            );
            scope.reload();
            go(org.id, n.id);
          }}
        >
          <Field label="Project name">
            <input
              required
              maxLength={255}
              name="name"
              placeholder="Payments"
            />
          </Field>
          <Field
            label="Project key (optional)"
            hint="Leave blank to generate a key."
          >
            <input
              name="key"
              pattern="[a-z0-9][a-z0-9\-]*"
              maxLength={255}
              placeholder="payments"
            />
          </Field>
        </FormDialog>
      )}
    </div>
  );
}
type Data = {
  envs: Env[];
  flags: Flag[];
  segments: Segment[];
  configs: Config[];
};
function ProjectView({
  project,
  role,
  page,
  flagId,
  change,
}: {
  project: Project;
  role: Role;
  page: string;
  flagId?: string;
  change: (p: string, id?: string) => void;
}) {
  const path = projectPath(project.id);
  const res = useResource<Data>(path, async () => {
    const [envs, flags, segments] = await Promise.all([
      api<Env[]>(path + "/environments"),
      api<Flag[]>(path + "/flags"),
      api<Segment[]>(path + "/segments"),
    ]);
    const configs = await Promise.all(
      envs.map((e) => api<Config[]>("/environments/" + e.id + "/flags")),
    );
    return {
      envs: envs.sort(envOrder),
      flags: flags.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)),
      segments,
      configs: configs.flat(),
    };
  });
  const [creating, setCreating] = useState(false);
  const toast = useToast();
  if (res.error) return <Problem error={res.error} retry={res.reload} />;
  if (!res.data) return <Busy />;
  const data = res.data,
    base = { ...data, project, role, reload: res.reload };
  const open = (f: Flag) => change("flags", f.id);
  if (page === "flags") {
    const flag = data.flags.find((f) => f.id === flagId);
    return flag ? (
      <FlagDetail
        key={flag.id}
        {...base}
        flag={flag}
        back={() => change("flags")}
      />
    ) : (
      <FlagList {...base} open={open} />
    );
  }
  if (page === "environments") return <Environments {...base} />;
  if (page === "segments") return <Segments {...base} />;
  if (page === "keys")
    return canAdmin(role) ? (
      <Keys {...base} />
    ) : (
      <Problem error="Administrator access is required to manage SDK keys." />
    );
  return (
    <>
      <Heading
        title="Project overview"
        sub={"Release configuration for " + project.name + "."}
        action={
          canEdit(role) && <Add onClick={() => setCreating(true)}>New flag</Add>
        }
      />
      <div className="stats">
        {[
          {
            name: "Feature flags",
            value: data.flags.length,
            Icon: FlagIcon,
            page: "flags",
            tone: "violet",
          },
          {
            name: "Environments",
            value: data.envs.length,
            Icon: Layers3,
            page: "environments",
            tone: "blue",
          },
          {
            name: "Segments",
            value: data.segments.length,
            Icon: UsersRound,
            page: "segments",
            tone: "mint",
          },
        ].map((s) => (
          <button key={s.name} className="stat" onClick={() => change(s.page)}>
            <div className="line">
              <span className={"tile " + s.tone}>
                <s.Icon size={20} />
              </span>
              <ChevronRight size={16} />
            </div>
            <strong>{s.value}</strong>
            <span>{s.name}</span>
          </button>
        ))}
      </div>
      <div className="section-heading">
        <h2>Environment status</h2>
        <button className="text-btn" onClick={() => change("environments")}>
          Manage environments
        </button>
      </div>
      <div className="panel env-summary">
        {data.envs.map((e) => {
          const configs = data.configs.filter((c) => c.environmentId === e.id),
            on = configs.filter((c) => c.enabled).length;
          return (
            <div className="env-line" key={e.id}>
              <div className="env-name">
                <span className={"env-icon " + e.key}>
                  <Layers3 size={18} />
                </span>
                <strong>{e.name}</strong>
              </div>
              <div className="track">
                <i
                  style={{
                    width:
                      (configs.length ? (on / configs.length) * 100 : 0) + "%",
                  }}
                />
              </div>
              <span>
                {on} of {configs.length} enabled
              </span>
            </div>
          );
        })}
      </div>
      <div className="section-heading">
        <h2>Recently updated flags</h2>
        <button className="text-btn" onClick={() => change("flags")}>
          View all flags
        </button>
      </div>
      <div className="panel">
        {data.flags.length ? (
          data.flags.slice(0, 5).map((f) => (
            <button key={f.id} className="recent" onClick={() => open(f)}>
              <span className="tile">
                <FlagIcon size={18} />
              </span>
              <span className="grow">
                <strong>{f.name}</strong>
                <code>{f.key}</code>
              </span>
              <Tag color="neutral">{f.valueType}</Tag>
              <small>{dateText(f.updatedAt)}</small>
              <ChevronRight size={16} />
            </button>
          ))
        ) : (
          <Empty title="No feature flags yet">
            Your project’s flag definitions will appear here.
          </Empty>
        )}
      </div>
      {creating && (
        <FlagForm
          project={project}
          close={() => setCreating(false)}
          saved={(f) => {
            res.reload();
            open(f);
            toast("Flag created");
          }}
        />
      )}
    </>
  );
}
