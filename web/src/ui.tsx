import { lazy, Suspense } from "react";
const ConsoleScene = lazy(() => import("./ConsoleScene"));
import {
  useEffect,
  useRef,
  useState,
  useContext,
  createContext,
  type ReactNode,
  type FormEvent,
} from "react";
import {
  Activity,
  AlertCircle,
  Check,
  ChevronDown,
  Flag as FlagIcon,
  Layers3,
  LoaderCircle,
  Plus,
  SlidersHorizontal,
  X,
} from "lucide-react";
import { errorText, type ValueType } from "./domain";

export function useResource<T>(key: string, load: () => Promise<T>) {
  const [version, setVersion] = useState(0);
  const [state, setState] = useState<{ key: string; data?: T; error?: string }>(
    { key: "" },
  );
  const loader = useRef(load);
  loader.current = load;
  useEffect(() => {
    let valid = true;
    setState((s) => (s.key === key ? { key, data: s.data } : { key }));
    loader
      .current()
      .then((data) => {
        if (valid) setState({ key, data });
      })
      .catch((e) => {
        if (valid) setState({ key, error: errorText(e) });
      });
    return () => {
      valid = false;
    };
  }, [key, version]);
  return {
    ...(state.key === key ? state : { key }),
    reload: () => setVersion((n) => n + 1),
  };
}
export const ToastContext = createContext<(msg: string) => void>(() => {});
export const useToast = () => useContext(ToastContext);
export function Brand() {
  return (
    <span className="brand">
      <img src="/switchly.svg" alt="" />
      Switchly
    </span>
  );
}
export function Busy() {
  return (
    <div className="notice" role="status">
      <LoaderCircle className="spin" size={18} /> Loading workspace…
    </div>
  );
}
export function Problem({
  error,
  retry,
}: {
  error: string;
  retry?: () => void;
}) {
  return (
    <div className="error" role="alert">
      <AlertCircle size={17} />
      <span>{error}</span>
      {retry && <button onClick={retry}>Retry</button>}
    </div>
  );
}
export function Empty({
  title,
  children,
  action,
}: {
  title: string;
  children?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className="empty">
      <span className="tile">
        <Layers3 size={23} />
      </span>
      <h3>{title}</h3>
      {children && <p>{children}</p>}
      {action}
    </div>
  );
}
export function Add({
  children,
  onClick,
}: {
  children: ReactNode;
  onClick: () => void;
}) {
  return (
    <button className="primary" onClick={onClick}>
      <Plus size={17} />
      {children}
    </button>
  );
}
export function Heading({
  title,
  sub,
  action,
}: {
  title: string;
  sub?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <header className="heading">
      <div>
        <h1>{title}</h1>
        {sub && <p>{sub}</p>}
      </div>
      {action}
    </header>
  );
}
export function Field({
  label,
  children,
  hint,
}: {
  label: string;
  children: ReactNode;
  hint?: string;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {hint && <small>{hint}</small>}
    </label>
  );
}
export function Select({
  children,
  ...props
}: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <span className="select">
      <select {...props}>{children}</select>
      <ChevronDown size={15} />
    </span>
  );
}
export function TypedInput({
  type,
  value,
  onChange,
  disabled = false,
  label = "Value",
}: {
  type: ValueType;
  value: string;
  onChange: (s: string) => void;
  disabled?: boolean;
  label?: string;
}) {
  return (
    <Field label={label}>
      {type === "BOOLEAN" ? (
        <Select
          value={value}
          disabled={disabled}
          onChange={(e) => onChange(e.target.value)}
        >
          <option>true</option>
          <option>false</option>
        </Select>
      ) : type === "JSON" ? (
        <textarea
          className="mono"
          rows={5}
          value={value}
          disabled={disabled}
          onChange={(e) => onChange(e.target.value)}
          spellCheck={false}
        />
      ) : (
        <input
          value={value}
          disabled={disabled}
          className={type === "NUMBER" ? "mono" : ""}
          required={type === "NUMBER"}
          onChange={(e) => onChange(e.target.value)}
        />
      )}
    </Field>
  );
}
export function Dialog({
  title,
  close,
  children,
  locked = false,
}: {
  title: string;
  close: () => void;
  children: ReactNode;
  locked?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current!;
    d.showModal();
    return () => d.close();
  }, []);
  return (
    <dialog
      ref={ref}
      onCancel={(e) => {
        e.preventDefault();
        if (!locked) close();
      }}
      aria-label={title}
    >
      <div className="dialog-head">
        <h2>{title}</h2>
        <button
          className="icon"
          aria-label="Close dialog"
          disabled={locked}
          onClick={close}
        >
          <X size={20} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
export function FormDialog({
  title,
  submit = "Save changes",
  close,
  onSave,
  children,
  danger = false,
}: {
  title: string;
  submit?: string;
  close: () => void;
  onSave: (data: FormData) => Promise<void>;
  children: ReactNode;
  danger?: boolean;
}) {
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  async function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (busy) return;
    const data = new FormData(e.currentTarget);
    setBusy(true);
    setError("");
    try {
      await onSave(data);
      close();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Dialog title={title} close={close} locked={busy}>
      <form onSubmit={save}>
        <div className="dialog-body">
          {children}
          {error && <Problem error={error} />}
        </div>
        <footer>
          <button type="button" disabled={busy} onClick={close}>
            Cancel
          </button>
          <button className={danger ? "danger" : "primary"} disabled={busy}>
            {busy ? "Saving…" : submit}
          </button>
        </footer>
      </form>
    </Dialog>
  );
}
export function Confirm({
  title,
  detail,
  submit,
  onSave,
  close,
}: {
  title: string;
  detail: string;
  submit: string;
  onSave: () => Promise<void>;
  close: () => void;
}) {
  return (
    <FormDialog {...{ title, submit, onSave, close }} danger>
      <p>{detail}</p>
    </FormDialog>
  );
}
export function Tag({
  children,
  color = "violet",
}: {
  children: ReactNode;
  color?: string;
}) {
  return <span className={"tag " + color}>{children}</span>;
}
export function EnvBadge({ name }: { name: string }) {
  return (
    <span
      className={
        "env-badge " +
        (["development", "staging", "production"].includes(name)
          ? name
          : "custom")
      }
    >
      <i />
      {name}
    </span>
  );
}
export function Toast({ text, close }: { text: string; close: () => void }) {
  return (
    <div className="toast" role="status">
      <Check size={18} />
      {text}
      <button
        className="icon"
        aria-label="Dismiss notification"
        onClick={close}
      >
        <X size={15} />
      </button>
    </div>
  );
}
export function ConsoleArt() {
  return (
    <div className="art" aria-hidden="true">
      <div className="art-grid" />
      <Suspense fallback={null}><ConsoleScene /></Suspense>
      <div className="instrument">
        <div className="plate">
          <i />
          <i />
          <i />
          <i />
          <div className="device-head">
            <SlidersHorizontal size={16} /> SWITCHLY
          </div>
          <div className="channels">
            {[0, 1, 2].map((v) => (
              <div className={"channel c" + v} key={v}>
                <b />
                <div className="rail">
                  <div className="knob" />
                </div>
                <em />
              </div>
            ))}
          </div>
          <div className="device-foot">
            <Activity size={18} />
            <span />
            <FlagIcon size={15} />
          </div>
        </div>
      </div>
    </div>
  );
}
export const field = (data: FormData, name: string) =>
  String(data.get(name) ?? "");
