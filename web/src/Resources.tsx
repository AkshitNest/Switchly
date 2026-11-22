import { useState } from "react";
import {
  Copy,
  FlaskConical,
  KeyRound,
  Layers3,
  Pencil,
  ScanLine,
  Trash2,
  UsersRound,
} from "lucide-react";
import { api, projectPath } from "./api";
import {
  canAdmin,
  canEdit,
  dateText,
  decode,
  type Env,
  type Project,
  type Role,
  type SdkKey,
  type Segment,
} from "./domain";
import {
  Add,
  Busy,
  Confirm,
  Dialog,
  Empty,
  EnvBadge,
  Field,
  FormDialog,
  Heading,
  Problem,
  Tag,
  field,
  useResource,
  useToast,
} from "./ui";
import { Conditions, draft, groupValue } from "./conditions";
type Base = { project: Project; role: Role; reload: () => void };
export function Environments({
  project,
  role,
  reload,
  envs,
}: { envs: Env[] } & Base) {
  const [edit, setEdit] = useState<Env | "new" | null>(null),
    [del, setDel] = useState<Env | null>(null);
  const toast = useToast();
  const current = edit && edit !== "new" ? edit : null;
  return (
    <>
      <Heading
        title="Environments"
        sub="Manage the deployment environments in this project."
        action={
          canAdmin(role) && (
            <Add onClick={() => setEdit("new")}>New environment</Add>
          )
        }
      />
      <div className="resource-grid">
        {envs.map((e) => (
          <article className="panel resource" key={e.id}>
            <div className="line">
              <span className="tile">
                <Layers3 />
              </span>
              {e.isDefault && <Tag color="neutral">Default</Tag>}
            </div>
            <h2>{e.name}</h2>
            <EnvBadge name={e.key} />
            <p>{e.description || "No description."}</p>
            {canAdmin(role) && (
              <div className="actions">
                <button onClick={() => setEdit(e)}>
                  <Pencil size={15} />
                  Edit
                </button>
                {!e.isDefault && (
                  <button
                    className="icon"
                    aria-label={"Delete " + e.name}
                    onClick={() => setDel(e)}
                  >
                    <Trash2 size={16} />
                  </button>
                )}
              </div>
            )}
          </article>
        ))}
      </div>
      {edit && (
        <FormDialog
          title={current ? "Edit environment" : "New environment"}
          close={() => setEdit(null)}
          submit={current ? "Save changes" : "Create environment"}
          onSave={async (d) => {
            const body = {
              name: field(d, "name"),
              description: field(d, "description"),
            };
            await api(
              current
                ? "/environments/" + current.id
                : projectPath(project.id) + "/environments",
              current ? "PUT" : "POST",
              current ? body : { ...body, key: field(d, "key") },
            );
            reload();
            toast("Environment saved");
          }}
        >
          <Field label="Name">
            <input
              required
              name="name"
              maxLength={255}
              defaultValue={current?.name}
            />
          </Field>
          {!current && (
            <Field label="Environment key">
              <input
                required
                name="key"
                pattern="[a-z0-9\-]+"
                maxLength={255}
                placeholder="preview"
              />
            </Field>
          )}
          <Field label="Description">
            <textarea
              name="description"
              defaultValue={current?.description || ""}
            />
          </Field>
        </FormDialog>
      )}
      {del && (
        <Confirm
          title="Delete environment?"
          detail={"Delete " + del.name + " and its flag configurations."}
          submit="Delete environment"
          close={() => setDel(null)}
          onSave={async () => {
            await api("/environments/" + del.id, "DELETE");
            reload();
            toast("Environment deleted");
          }}
        />
      )}
    </>
  );
}
export function Segments({
  project,
  role,
  reload,
  segments,
}: { segments: Segment[] } & Base) {
  const [edit, setEdit] = useState<Segment | "new" | null>(null),
    [del, setDel] = useState<Segment | null>(null),
    [preview, setPreview] = useState<Segment | null>(null);
  const toast = useToast();
  const path = projectPath(project.id) + "/segments";
  return (
    <>
      <Heading
        title="Segments"
        sub="Define reusable audiences for your targeting rules."
        action={
          canEdit(role) && <Add onClick={() => setEdit("new")}>New segment</Add>
        }
      />
      {!segments.length ? (
        <Empty title="No segments yet">
          Create an audience using context attributes.
        </Empty>
      ) : (
        <div className="resource-grid">
          {segments.map((s) => (
            <article className="panel resource" key={s.id}>
              <div className="line">
                <span className="tile mint">
                  <UsersRound />
                </span>
                <Tag color="neutral">{s.conditions.match}</Tag>
              </div>
              <h2>{s.name}</h2>
              <code>{s.key}</code>
              <p>
                {s.description ||
                  s.conditions.conditions.length + " conditions"}
              </p>
              <div className="actions">
                <button onClick={() => setPreview(s)}>
                  <ScanLine size={16} />
                  Preview
                </button>
                <button onClick={() => setEdit(s)}>
                  {canEdit(role) ? "Edit" : "View definition"}
                </button>
                {canEdit(role) && (
                  <button
                    className="icon"
                    aria-label={"Delete " + s.name}
                    onClick={() => setDel(s)}
                  >
                    <Trash2 size={16} />
                  </button>
                )}
              </div>
            </article>
          ))}
        </div>
      )}
      {edit && (
        <SegmentForm
          segment={edit === "new" ? null : edit}
          path={path}
          writable={canEdit(role)}
          close={() => setEdit(null)}
          saved={() => {
            reload();
            toast("Segment saved");
          }}
        />
      )}
      {preview && (
        <Preview segment={preview} path={path} close={() => setPreview(null)} />
      )}
      {del && (
        <Confirm
          title="Delete segment?"
          detail={
            "Remove " +
            del.name +
            " from this project. Referenced segments must first be removed from their rules."
          }
          close={() => setDel(null)}
          submit="Delete segment"
          onSave={async () => {
            await api(path + "/" + del.key, "DELETE");
            reload();
            toast("Segment deleted");
          }}
        />
      )}
    </>
  );
}
function SegmentForm({
  segment,
  path,
  writable,
  close,
  saved,
}: {
  segment: Segment | null;
  path: string;
  writable: boolean;
  close: () => void;
  saved: () => void;
}) {
  const [group, setGroup] = useState(() => draft(segment?.conditions));
  const form = (
    <>
      <Field label="Name">
        <input
          required
          name="name"
          maxLength={255}
          defaultValue={segment?.name}
          disabled={!writable}
        />
      </Field>
      <Field label="Segment key">
        <input
          required
          name="key"
          pattern="[a-z0-9][a-z0-9\-]*"
          maxLength={255}
          defaultValue={segment?.key}
          disabled={!!segment}
        />
      </Field>
      <Field label="Description">
        <textarea
          name="description"
          maxLength={4096}
          defaultValue={segment?.description || ""}
          disabled={!writable}
        />
      </Field>
      <Conditions value={group} onChange={setGroup} disabled={!writable} />
    </>
  );
  if (!writable)
    return (
      <Dialog title="Segment definition" close={close}>
        <div className="dialog-body">{form}</div>
      </Dialog>
    );
  return (
    <FormDialog
      title={segment ? "Edit segment" : "New segment"}
      submit={segment ? "Save changes" : "Create segment"}
      close={close}
      onSave={async (d) => {
        const body = {
          name: field(d, "name"),
          description: field(d, "description"),
          conditions: groupValue(group),
        };
        await api(
          segment ? path + "/" + segment.key : path,
          segment ? "PUT" : "POST",
          segment ? body : { ...body, key: field(d, "key") },
        );
        saved();
      }}
    >
      {form}
    </FormDialog>
  );
}
function Preview({
  segment,
  path,
  close,
}: {
  segment: Segment;
  path: string;
  close: () => void;
}) {
  const [input, setInput] = useState("{}"),
    [result, setResult] = useState<boolean | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  return (
    <Dialog title={"Preview " + segment.name} close={close} locked={busy}>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          setResult(null);
          setError("");
          try {
            const context = decode(input);
            const r = await api<{ member: boolean }>(
              path + "/" + segment.key + "/evaluate",
              "POST",
              { context },
            );
            setResult(r.member);
          } catch (e) {
            setError(e instanceof Error ? e.message : "Preview failed");
          } finally {
            setBusy(false);
          }
        }}
      >
        <div className="dialog-body">
          <Field
            label="Evaluation context"
            hint='Use JSON, for example {"country":"IN","userId":"user-123"}.'
          >
            <textarea
              className="mono"
              rows={7}
              value={input}
              onChange={(e) => {
                setInput(e.target.value);
                setResult(null);
              }}
            />
          </Field>
          {error && <Problem error={error} />}{" "}
          {result !== null && (
            <div role="status">
              <Tag color={result ? "green" : "neutral"}>
                {result ? "Matches segment" : "Outside segment"}
              </Tag>
            </div>
          )}
        </div>
        <footer>
          <button className="primary" disabled={busy}>
            <FlaskConical size={16} />
            Run preview
          </button>
        </footer>
      </form>
    </Dialog>
  );
}
export function Keys({ project, envs }: { project: Project; envs: Env[] }) {
  const path = projectPath(project.id) + "/sdk-keys";
  const list = useResource(path, () => api<SdkKey[]>(path));
  const [creating, setCreating] = useState(false),
    [secret, setSecret] = useState(""),
    [del, setDel] = useState<SdkKey | null>(null);
  const toast = useToast();
  return (
    <>
      <Heading
        title="SDK keys"
        sub="Manage server-side credentials and their environment access."
        action={<Add onClick={() => setCreating(true)}>Issue SDK key</Add>}
      />
      {list.error ? (
        <Problem error={list.error} retry={list.reload} />
      ) : !list.data ? (
        <Busy />
      ) : !list.data.length ? (
        <Empty title="No SDK keys yet">
          Issue a scoped key to connect your backend to this project.
        </Empty>
      ) : (
        <div className="stack">
          {list.data.map((k) => (
            <div className="panel list-row" key={k.id}>
              <span className="tile">
                <KeyRound />
              </span>
              <div className="grow">
                <strong>{k.name}</strong>
                <div className="actions wrap">
                  {k.environmentKeys.map((e) => (
                    <EnvBadge key={e} name={e} />
                  ))}
                </div>
              </div>
              <Tag color={k.revokedAt ? "neutral" : "green"}>
                {k.revokedAt ? "Revoked" : "Active"}
              </Tag>
              <small>{dateText(k.createdAt)}</small>
              {!k.revokedAt && (
                <button onClick={() => setDel(k)}>Revoke</button>
              )}
            </div>
          ))}
        </div>
      )}
      {creating && (
        <FormDialog
          title="Issue SDK key"
          submit="Issue key"
          close={() => setCreating(false)}
          onSave={async (d) => {
            const environmentKeys = d.getAll("env");
            if (!environmentKeys.length)
              throw Error("Select at least one environment.");
            const issued = await api<{ secret: string }>(path, "POST", {
              name: field(d, "name"),
              environmentKeys,
            });
            setSecret(issued.secret);
            list.reload();
          }}
        >
          <Field label="Key name">
            <input
              required
              maxLength={255}
              name="name"
              placeholder="Payments backend"
            />
          </Field>
          <fieldset>
            <legend>Permitted environments</legend>
            {envs.map((e) => (
              <label key={e.id} className="check">
                <input type="checkbox" name="env" value={e.key} />
                {e.name}
              </label>
            ))}
          </fieldset>
        </FormDialog>
      )}
      {secret && (
        <Dialog title="Copy your SDK secret" close={() => setSecret("")}>
          <div className="dialog-body">
            <p>
              Save this secret in your server’s secret manager. Closing this
              dialog hides it permanently.
            </p>
            <Field label="One-time SDK secret">
              <input
                className="mono"
                readOnly
                value={secret}
                onFocus={(e) => e.target.select()}
              />
            </Field>
          </div>
          <footer>
            <button
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText(secret);
                  toast("Secret copied");
                } catch {
                  toast("Select the secret and copy it manually");
                }
              }}
            >
              <Copy size={16} />
              Copy
            </button>
            <button className="primary" onClick={() => setSecret("")}>
              Done
            </button>
          </footer>
        </Dialog>
      )}
      {del && (
        <Confirm
          title="Revoke SDK key?"
          detail={
            "Servers using " + del.name + " will lose access immediately."
          }
          submit="Revoke key"
          close={() => setDel(null)}
          onSave={async () => {
            await api(path + "/" + del.id, "DELETE");
            list.reload();
            toast("SDK key revoked");
          }}
        />
      )}
    </>
  );
}
