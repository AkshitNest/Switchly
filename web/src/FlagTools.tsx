import { useState } from "react";
import { WandSparkles, ScanLine } from "lucide-react";
import { api, projectPath } from "./api";
import { decode, encode, errorText, typed, type Json, type ValueType } from "./domain";
import { Field, Problem } from "./ui";

export type FlagDraft = { name: string; key: string; description: string; valueType: ValueType; defaultText: string };
export function DraftAssistant({ project, apply }: { project: string; apply: (draft: FlagDraft) => void }) {
  const [brief, setBrief] = useState(""), [busy, setBusy] = useState(false), [error, setError] = useState("");
  async function generate() {
    setBusy(true); setError("");
    try {
      const result = await api<FlagDraft>(projectPath(project) + "/flag-drafts", "POST", { brief });
      typed(result.valueType, result.defaultText);
      apply(result);
    } catch (e) { setError(errorText(e)); } finally { setBusy(false); }
  }
  return <details className="draft-tool"><summary><WandSparkles size={16}/>Draft with AI</summary>
    <Field label="Release brief" hint="Your local model fills in the fields below for review.">
      <textarea value={brief} maxLength={1500} rows={2} onChange={e => setBrief(e.target.value)} placeholder="A toggle for the new checkout, off by default" /></Field>
    <button type="button" disabled={!brief.trim() || busy} onClick={generate}>{busy ? "Drafting…" : "Generate draft"}</button>
    {error && <Problem error={error}/>}
  </details>;
}

export function EvaluationPreview({ path }: {path: string}) {
  const [input, setInput] = useState('{"userId":"customer-42"}');
  const [result, setResult] = useState<{value: Json; reason: string} | null>(null);
  const [error,setError] = useState(""), [busy,setBusy] = useState(false);
  return <section className="panel pad preview"><div className="section-title"><ScanLine size={18}/><h3>Test a user</h3></div>
    <form onSubmit={async e => {
      e.preventDefault(); setBusy(true); setError(""); setResult(null);
      try {setResult(await api(path + "/preview", "POST", {context: decode(input)}));}
      catch (e) {setError(errorText(e));} finally {setBusy(false);}
    }}>
    <Field label="Evaluation context" hint="Run the saved rules and rollout for this environment.">
      <textarea rows={2} className="mono" required disabled={busy} value={input} onChange={e => {setInput(e.target.value); setResult(null);}}/></Field>
    <button disabled={busy}>{busy ? "Evaluating…" : "Evaluate flag"}</button></form>
    {error && <Problem error={error}/>}
    {result && <div className="preview-result" role="status"><strong>{encode(result.value)}</strong><small>{result.reason.replaceAll("_"," ").toLowerCase()}</small></div>}
  </section>;
}
