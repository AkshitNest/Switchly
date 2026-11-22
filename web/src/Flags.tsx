import { useState } from "react";
import {
  ArrowDown,
  ArrowUp,
  ChevronRight,
  ChevronsLeft,
  Flag as FlagIcon,
  GitBranch,
  ListOrdered,
  Pencil,
  Plus,
  Search,
  SlidersHorizontal,
  Trash2,
  Workflow,
} from "lucide-react";
import { api, configPath, projectPath } from "./api";
import {
  canEdit,
  dateText,
  encode,
  errorText,
  initialValue,
  typed,
  valueText,
  type Config,
  type Env,
  type Flag,
  type Project,
  type Role,
  type Rule,
  type Segment,
  type ValueType,
} from "./domain";
import {
  Add,
  Busy,
  Confirm,
  Empty,
  Field,
  FormDialog,
  Heading,
  Problem,
  Select,
  Tag,
  TypedInput,
  field,
  useResource,
  useToast,
} from "./ui";
import { DraftAssistant, EvaluationPreview } from "./FlagTools";
import { Conditions, draft, groupValue, type GroupDraft } from "./conditions";

type Base = { project: Project; role: Role; envs: Env[]; reload: () => void };
export function FlagForm({
  flag,
  project,
  close,
  saved,
}: {
  flag?: Flag;
  project: Project;
  close: () => void;
  saved: (f: Flag) => void;
}) {
  const [name, setName] = useState(flag?.name || "");
  const [key, setKey] = useState(flag?.key || "");
  const [description, setDescription] = useState(flag?.description || "");
  const toast = useToast();
  const [type, setType] = useState<ValueType>(flag?.valueType || "BOOLEAN");
  const [value, setValue] = useState(
    flag ? valueText(type, flag.defaultValue) : "false",
  );
  return (
    <FormDialog
      title={flag ? "Edit logical flag" : "New feature flag"}
      submit={flag ? "Save changes" : "Create flag"}
      close={close}
      onSave={async (d) => {
        const body = {
          name: field(d, "name"),
          description: field(d, "description"),
          defaultValue: typed(type, value),
        };
        saved(
          await api<Flag>(
            flag ? "/flags/" + flag.id : projectPath(project.id) + "/flags",
            flag ? "PUT" : "POST",
            flag ? body : { ...body, key: field(d, "key"), valueType: type },
          ),
        );
      }}
    >
      {!flag && <DraftAssistant project={project.id} apply={draft => {
        setName(draft.name); setKey(draft.key); setDescription(draft.description);
        setType(draft.valueType); setValue(draft.defaultText);
        toast("Draft ready for review");
      }} />}
      <Field label="Flag name">
        <input
          name="name"
          required
          maxLength={255}
          value={name} onChange={e => setName(e.target.value)}
          placeholder="New checkout"
        />
      </Field>
      <Field label="Flag key">
        <input
          name="key"
          required
          pattern="[a-z0-9\-]+"
          maxLength={255}
          value={key} onChange={e => setKey(e.target.value)}
          disabled={!!flag}
          placeholder="new-checkout"
        />
      </Field>
      <Field label="Description">
        <textarea
          name="description"
          maxLength={1000}
          value={description} onChange={e => setDescription(e.target.value)}
        />
      </Field>
      <Field label="Value type">
        <Select
          disabled={!!flag}
          value={type}
          onChange={(e) => {
            const t = e.target.value as ValueType;
            setType(t);
            setValue(initialValue[t]);
          }}
        >
          {Object.keys(initialValue).map((t) => (
            <option key={t}>{t}</option>
          ))}
        </Select>
      </Field>
      <TypedInput
        type={type}
        value={value}
        onChange={setValue}
        label="Logical default value"
      />
      <p className="hint">
        This fallback applies when an environment is disabled or a request is
        excluded.
      </p>
    </FormDialog>
  );
}
export function FlagList({
  project,
  role,
  envs,
  reload,
  flags,
  configs,
  open,
}: { flags: Flag[]; configs: Config[]; open: (f: Flag) => void } & Base) {
  const [query, setQuery] = useState(""),
    [envId, setEnvId] = useState(envs[0]?.id || ""),
    [creating, setCreating] = useState(false),
    [filter, setFilter] = useState("all");
  const toast = useToast();
  const byFlag = new Map(
    configs.filter((c) => c.environmentId === envId).map((c) => [c.flagId, c]),
  );
  const visible = flags.filter(
    (f) =>
      (f.name + " " + f.key).toLowerCase().includes(query.toLowerCase()) &&
      (filter === "all" || byFlag.get(f.id)?.enabled === (filter === "on")),
  );
  return (
    <>
      <Heading
        title="Feature flags"
        sub="Project definitions and their environment-specific states."
        action={
          canEdit(role) && <Add onClick={() => setCreating(true)}>New flag</Add>
        }
      />
      <div className="panel">
        <div className="toolbar">
          <label className="search">
            <Search size={17} />
            <input
              aria-label="Search flags"
              placeholder="Search flags…"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
          </label>
          <Select
            aria-label="Filter environment"
            value={envId}
            onChange={(e) => setEnvId(e.target.value)}
          >
            {envs.map((e) => (
              <option value={e.id} key={e.id}>
                {e.name}
              </option>
            ))}
          </Select>
          <Select
            aria-label="Filter state"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          >
            <option value="all">All states</option>
            <option value="on">Enabled</option>
            <option value="off">Disabled</option>
          </Select>
        </div>
        {!visible.length ? (
          <Empty
            title={flags.length ? "No matching flags" : "No feature flags yet"}
          >
            {flags.length
              ? "Change the search or state filter."
              : "Create a logical flag to configure it across environments."}
          </Empty>
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Flag</th>
                  <th>Type</th>
                  <th>Environment state</th>
                  <th>Updated</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {visible.map((f) => {
                  const c = byFlag.get(f.id);
                  return (
                    <tr key={f.id}>
                      <td>
                        <button className="row-link" onClick={() => open(f)}>
                          {f.name}
                        </button>
                        <code>{f.key}</code>
                      </td>
                      <td>
                        <Tag color="neutral">{f.valueType}</Tag>
                      </td>
                      <td>
                        <Tag color={c?.enabled ? "green" : "neutral"}>
                          {c
                            ? c.enabled
                              ? "Enabled"
                              : "Disabled"
                            : "Unconfigured"}
                        </Tag>
                      </td>
                      <td>{dateText(f.updatedAt)}</td>
                      <td>
                        <button
                          className="icon"
                          aria-label={"Open " + f.name}
                          onClick={() => open(f)}
                        >
                          <ChevronRight size={17} />
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
      {creating && (
        <FlagForm
          project={project}
          close={() => setCreating(false)}
          saved={(f) => {
            reload();
            toast("Flag created");
            open(f);
          }}
        />
      )}
    </>
  );
}
export function FlagDetail({
  project,
  role,
  envs,
  reload,
  flag,
  segments,
  back,
}: { flag: Flag; segments: Segment[]; back: () => void } & Base) {
  const [envId, setEnvId] = useState(envs[0]?.id || ""),
    [edit, setEdit] = useState(false),
    [del, setDel] = useState(false);
  const toast = useToast();
  const env = envs.find((e) => e.id === envId) || envs[0];
  return (
    <>
      <button className="back text-btn" onClick={back}>
        <ChevronsLeft size={15} />
        Feature flags
      </button>
      <Heading
        title={flag.name}
        sub={
          <>
            <code>{flag.key}</code> · {flag.valueType}
          </>
        }
        action={
          canEdit(role) && (
            <div className="actions">
              <button onClick={() => setEdit(true)}>
                <Pencil size={16} />
                Edit logical flag
              </button>
              <button
                className="icon"
                aria-label="Delete flag"
                onClick={() => setDel(true)}
              >
                <Trash2 size={17} />
              </button>
            </div>
          )
        }
      />
      <div className="panel definition">
        <span className="tile">
          <FlagIcon size={21} />
        </span>
        <div className="grow">
          <strong>Logical default</strong>
          <p>
            {flag.description ||
              "Fallback value for this flag across the project."}
          </p>
        </div>
        <code className="literal">{encode(flag.defaultValue)}</code>
      </div>
      <div className="section-heading">
        <h2>Environment configuration</h2>
        <Select
          aria-label="Configuration environment"
          value={env?.id || ""}
          onChange={(e) => setEnvId(e.target.value)}
        >
          {envs.map((e) => (
            <option value={e.id} key={e.id}>
              {e.name}
            </option>
          ))}
        </Select>
      </div>
      {env && (
        <EnvironmentConfig
          key={env.id + flag.id}
          env={env}
          flag={flag}
          segments={segments}
          writable={canEdit(role)}
          updated={reload}
        />
      )}
      {edit && (
        <FlagForm
          flag={flag}
          project={project}
          close={() => setEdit(false)}
          saved={() => {
            reload();
            toast("Logical flag updated");
          }}
        />
      )}
      {del && (
        <Confirm
          title="Delete feature flag?"
          detail={
            "Delete " +
            flag.name +
            " and its configurations in every environment."
          }
          submit="Delete flag"
          close={() => setDel(false)}
          onSave={async () => {
            await api("/flags/" + flag.id, "DELETE");
            reload();
            back();
            toast("Flag deleted");
          }}
        />
      )}
    </>
  );
}
function EnvironmentConfig({
  env,
  flag,
  segments,
  writable,
  updated,
}: {
  env: Env;
  flag: Flag;
  segments: Segment[];
  writable: boolean;
  updated: () => void;
}) {
  const path = configPath(env.id, flag.id),
    res = useResource(path, async () => {
      const [config, rules, rollout] = await Promise.all([
        api<Config>(path),
        api<Rule[]>(path + "/targeting"),
        api<{ percentage: number | null }>(path + "/rollout"),
      ]);
      return {
        config,
        rules,
        pct: rollout.percentage === null ? null : Number(rollout.percentage),
      };
    });
  const [edit, setEdit] = useState(""),
    [value, setValue] = useState(""),
    [pct, setPct] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const toast = useToast();
  if (res.error) return <Problem error={res.error} retry={res.reload} />;
  if (!res.data) return <Busy />;
  const { config, rules, pct: percent } = res.data;
  function saved(msg: string) {
    res.reload();
    updated();
    toast(msg);
  }
  return (
    <div className="stack">
      <section className="panel pad">
        <div className="line">
          <div>
            <div className="section-title">
              <SlidersHorizontal size={19} />
              <h3>Flag state and value</h3>
            </div>
            <p>
              {env.name} · {config.enabled ? "Enabled" : "Disabled"}
            </p>
          </div>
          <button
            className={"switch " + (config.enabled ? "on" : "")}
            role="switch"
            aria-checked={config.enabled}
            aria-label={"Enable flag in " + env.name}
            disabled={!writable || busy}
            onClick={async () => {
              setBusy(true);
              setError("");
              try {
                await api(path + "/state", "PUT", { enabled: !config.enabled });
                saved("Environment state updated");
              } catch (e) {
                setError(errorText(e));
              } finally {
                setBusy(false);
              }
            }}
          >
            <span />
          </button>
        </div>
        {error && <Problem error={error} />}
        <div className="value-row">
          <div>
            <small>Configured value</small>
            <pre>{encode(config.value, true)}</pre>
          </div>
          {writable && (
            <button
              onClick={() => {
                setValue(valueText(flag.valueType, config.value));
                setEdit("value");
              }}
            >
              Edit value
            </button>
          )}
        </div>
      </section>
      <section className="panel pad">
        <div className="line">
          <div className="section-title">
            <GitBranch size={20} />
            <h3>Targeting rules</h3>
          </div>
          {writable && (
            <button onClick={() => setEdit("rules")}>Edit rules</button>
          )}
        </div>
        <p>
          Rules run in order. The first matching rule determines whether the
          request continues to rollout.
        </p>
        {rules.length ? (
          <ol className="rule-list">
            {rules.map((r, i) => (
              <li key={i}>
                <Tag>{r.action}</Tag>
                <span>
                  {r.segmentKey
                    ? "Segment · " + r.segmentKey
                    : r.conditions?.match +
                      " · " +
                      r.conditions?.conditions
                        .map(
                          (c) =>
                            c.attribute +
                            " " +
                            c.operator.toLowerCase().replaceAll("_", " ") +
                            (c.value === undefined
                              ? ""
                              : " " + encode(c.value)),
                        )
                        .join(", ")}
                </span>
              </li>
            ))}
          </ol>
        ) : (
          <div className="inset">
            No rules. Every context continues to rollout.
          </div>
        )}
      </section>
      <section className="panel pad">
        <div className="line">
          <div className="section-title">
            <Workflow size={20} />
            <h3>Percentage rollout</h3>
          </div>
          {writable && (
            <button
              onClick={() => {
                setPct(percent === null ? "" : String(percent));
                setEdit("rollout");
              }}
            >
              Edit rollout
            </button>
          )}
        </div>
        <strong className="metric-small">
          {percent === null ? "Unset" : percent + "%"}
        </strong>
        <p>
          {percent === null
            ? "Eligible requests receive the environment value."
            : "Eligible users are assigned a stable bucket. Missing user IDs receive the logical default."}
        </p>
      </section>
      <div className="guidance">
        <ListOrdered size={19} />
        <div>
          <strong>Evaluation behavior</strong>
          <p>
            {!config.enabled
              ? "Disabled in " +
                env.name +
                ". Every request receives the logical default."
              : rules.length
                ? "The first matching rule wins. DEFAULT and no match return the logical default. A SERVE match continues to rollout."
                : percent === null
                  ? "Every request receives the configured environment value."
                  : "All requests reach the percentage rollout. Excluded or unidentified users receive the logical default."}
          </p>
        </div>
      </div>
      <EvaluationPreview key={path + config.updatedAt + percent + JSON.stringify(rules)} path={path} />
      {edit === "value" && (
        <FormDialog
          title={"Edit value in " + env.name}
          close={() => setEdit("")}
          onSave={async () => {
            await api(path + "/value", "PUT", {
              value: typed(flag.valueType, value),
            });
            saved("Environment value saved");
          }}
        >
          <TypedInput
            type={flag.valueType}
            value={value}
            onChange={setValue}
            label="Environment value"
          />
        </FormDialog>
      )}
      {edit === "rollout" && (
        <FormDialog
          title={"Edit rollout in " + env.name}
          close={() => setEdit("")}
          onSave={async () => {
            await api(path + "/rollout", "PUT", {
              percentage: pct === "" ? null : Number(pct),
            });
            saved("Rollout saved");
          }}
        >
          <div className="rollout-picker"><strong>{pct === "" ? "Unset" : pct + "%"}</strong>
            <input aria-label="Rollout slider" type="range" min="0" max="100" step="1" value={pct || 0} onChange={e => setPct(e.target.value)} />
            <div className="presets">{[0, 10, 25, 50, 100].map(n =>
              <button key={n} type="button" aria-pressed={pct === String(n)} onClick={() => setPct(String(n))}>{n}%</button>)}
              <button type="button" onClick={() => setPct("")}>Clear</button></div>
          </div>
          <Field
            label="Percentage"
            hint="Enter 0–100. Leave blank to clear the rollout."
          >
            <input
              type="number"
              min="0"
              max="100"
              step="1"
              value={pct}
              onChange={(e) => setPct(e.target.value)}
            />
          </Field>
          <p>
            Every configured percentage requires a stable user ID, including
            100%.
          </p>
        </FormDialog>
      )}
      {edit === "rules" && (
        <RulesEditor
          rules={rules}
          segments={segments}
          close={() => setEdit("")}
          save={async (next) => {
            await api(path + "/targeting", "PUT", next);
            saved("Targeting rules saved");
          }}
        />
      )}
    </div>
  );
}
type RuleDraft = {
  id: number;
  action: Rule["action"];
  source: "group" | "segment";
  segment: string;
  group: GroupDraft;
};
function RulesEditor({
  rules,
  segments,
  close,
  save,
}: {
  rules: Rule[];
  segments: Segment[];
  close: () => void;
  save: (r: Rule[]) => Promise<void>;
}) {
  const [rows, setRows] = useState<RuleDraft[]>(() =>
    rules.map((r, i) => ({
      id: i,
      action: r.action,
      source: r.segmentKey ? "segment" : "group",
      segment: r.segmentKey || "",
      group: draft(r.conditions),
    })),
  );
  const change = (i: number, patch: Partial<RuleDraft>) =>
    setRows(rows.map((r, j) => (i === j ? { ...r, ...patch } : r)));
  const move = (i: number, dir: number) => {
    const next = [...rows];
    [next[i], next[i + dir]] = [next[i + dir], next[i]];
    setRows(next);
  };
  return (
    <FormDialog
      title="Edit targeting rules"
      submit="Save rules"
      close={close}
      onSave={async () => {
        const next = rows.map((r) =>
          r.source === "segment"
            ? { action: r.action, segmentKey: r.segment }
            : { action: r.action, conditions: groupValue(r.group) },
        );
        await save(next);
      }}
    >
      <p>First match wins. Changes take effect when you save the rules.</p>
      {rows.map((r, i) => (
        <section className="rule-editor" key={r.id}>
          <div className="line">
            <h3>Rule {i + 1}</h3>
            <div className="actions">
              <button
                type="button"
                className="icon"
                aria-label={"Move rule " + (i + 1) + " up"}
                disabled={!i}
                onClick={() => move(i, -1)}
              >
                <ArrowUp size={15} />
              </button>
              <button
                type="button"
                className="icon"
                aria-label={"Move rule " + (i + 1) + " down"}
                disabled={i === rows.length - 1}
                onClick={() => move(i, 1)}
              >
                <ArrowDown size={15} />
              </button>
              <button
                type="button"
                className="icon"
                aria-label={"Remove rule " + (i + 1)}
                onClick={() => setRows(rows.filter((_, j) => j !== i))}
              >
                <Trash2 size={15} />
              </button>
            </div>
          </div>
          <div className="two">
            <Field label="Action">
              <Select
                value={r.action}
                onChange={(e) =>
                  change(i, { action: e.target.value as Rule["action"] })
                }
              >
                <option value="SERVE">Serve environment value</option>
                <option value="DEFAULT">Use logical default</option>
              </Select>
            </Field>
            <Field label="Match using">
              <Select
                value={r.source}
                onChange={(e) =>
                  change(i, {
                    source: e.target.value as RuleDraft["source"],
                    segment: r.segment || segments[0]?.key || "",
                  })
                }
              >
                <option value="group">Conditions</option>
                <option value="segment" disabled={!segments.length}>
                  Reusable segment
                </option>
              </Select>
            </Field>
          </div>
          {r.source === "segment" ? (
            <Field label="Segment">
              <Select
                required
                value={r.segment}
                onChange={(e) => change(i, { segment: e.target.value })}
              >
                {segments.map((s) => (
                  <option key={s.key} value={s.key}>
                    {s.name}
                  </option>
                ))}
              </Select>
            </Field>
          ) : (
            <Conditions
              value={r.group}
              onChange={(group) => change(i, { group })}
            />
          )}
        </section>
      ))}
      {rows.length < 32 && (
        <button
          type="button"
          onClick={() =>
            setRows([
              ...rows,
              {
                id: Math.max(0, ...rows.map((r) => r.id)) + 1,
                action: "SERVE",
                source: "group",
                segment: "",
                group: draft(),
              },
            ])
          }
        >
          <Plus size={16} />
          Add rule
        </button>
      )}
    </FormDialog>
  );
}
