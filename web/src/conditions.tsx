import { Plus, Trash2 } from "lucide-react";
import {
  decode,
  encode,
  firstGroup,
  typed,
  type Group,
  type Condition,
  type Json,
} from "./domain";
import { Field, Select } from "./ui";

const attrs = [
  "userId",
  "email",
  "country",
  "device",
  "platform",
  "appVersion",
];
const tests = [
  "EQUALS",
  "NOT_EQUALS",
  "CONTAINS",
  "STARTS_WITH",
  "ENDS_WITH",
  "IN",
  "NOT_IN",
  "EXISTS",
  "NOT_EXISTS",
];
const comparisons = [
  "GREATER_THAN",
  "GREATER_THAN_OR_EQUAL",
  "LESS_THAN",
  "LESS_THAN_OR_EQUAL",
];
const presence = (op: string) => op === "EXISTS" || op === "NOT_EXISTS";
const list = (op: string) => op === "IN" || op === "NOT_IN";
type Row = {
  attribute: string;
  custom: string;
  operator: string;
  kind: "string" | "number" | "boolean";
  input: string;
};
export type GroupDraft = { match: "AND" | "OR"; rows: Row[] };
export function draft(group: Group = firstGroup()): GroupDraft {
  return {
    match: group.match,
    rows: group.conditions.map((c) => ({
      attribute: c.attribute.startsWith("custom.") ? "custom" : c.attribute,
      custom: c.attribute.startsWith("custom.") ? c.attribute.slice(7) : "",
      operator: c.operator,
      kind:
        typeof c.value === "boolean"
          ? "boolean"
          : c.value !== undefined && typeof c.value !== "string"
            ? "number"
            : "string",
      input:
        c.value === undefined
          ? ""
          : typeof c.value === "string"
            ? c.value
            : encode(c.value),
    })),
  };
}
export function groupValue(g: GroupDraft): Group {
  const conditions: Condition[] = g.rows.map((r) => {
    const custom = r.attribute === "custom";
    if (custom && !/^[a-zA-Z][a-zA-Z0-9_]{0,63}$/.test(r.custom))
      throw Error("Enter a valid custom attribute name.");
    const c: Condition = {
      attribute: custom ? "custom." + r.custom : r.attribute,
      operator: r.operator,
    };
    if (presence(r.operator)) return c;
    if (list(r.operator)) {
      let v: Json;
      try {
        v = decode(r.input);
      } catch {
        throw Error("Enter a JSON array for the membership values.");
      }
      if (!Array.isArray(v) || !v.length || v.length > 100)
        throw Error("Enter an array of 1–100 values.");
      c.value = v;
    } else if (comparisons.includes(r.operator))
      c.value = typed("NUMBER", r.input);
    else if (custom && ["EQUALS", "NOT_EQUALS"].includes(r.operator))
      c.value = typed(
        ({ string: "STRING", number: "NUMBER", boolean: "BOOLEAN" } as const)[
          r.kind
        ],
        r.input,
      );
    else c.value = r.input;
    return c;
  });
  return { match: g.match, conditions };
}
export function Conditions({
  value,
  onChange,
  disabled = false,
}: {
  value: GroupDraft;
  onChange: (v: GroupDraft) => void;
  disabled?: boolean;
}) {
  const update = (index: number, patch: Partial<Row>) =>
    onChange({
      ...value,
      rows: value.rows.map((r, i) => (i === index ? { ...r, ...patch } : r)),
    });
  return (
    <fieldset className="condition-set" disabled={disabled}>
      <legend>Conditions</legend>
      <Field label="Match conditions">
        <Select
          value={value.match}
          onChange={(e) =>
            onChange({ ...value, match: e.target.value as Group["match"] })
          }
        >
          <option value="AND">All conditions (AND)</option>
          <option value="OR">Any condition (OR)</option>
        </Select>
      </Field>
      {value.rows.map((r, i) => (
        <div className="condition" key={i}>
          <div className="line">
            <strong>Condition {i + 1}</strong>
            {!disabled && value.rows.length > 1 && (
              <button
                type="button"
                className="icon"
                aria-label={"Remove condition " + (i + 1)}
                onClick={() =>
                  onChange({
                    ...value,
                    rows: value.rows.filter((_, j) => i !== j),
                  })
                }
              >
                <Trash2 size={15} />
              </button>
            )}
          </div>
          <div className="two">
            <Field label="Attribute">
              <Select
                value={r.attribute}
                onChange={(e) =>
                  update(i, {
                    attribute: e.target.value,
                    operator: "EQUALS",
                    input: "",
                    kind: "string",
                  })
                }
              >
                {[...attrs, "custom"].map((a) => (
                  <option key={a}>{a}</option>
                ))}
              </Select>
            </Field>
            <Field label="Operator">
              <Select
                value={r.operator}
                onChange={(e) =>
                  update(i, {
                    operator: e.target.value,
                    input: list(e.target.value) ? "[]" : r.input,
                  })
                }
              >
                {[
                  ...tests,
                  ...(r.attribute === "custom" ? comparisons : []),
                ].map((o) => (
                  <option key={o} value={o}>
                    {o.toLowerCase().replaceAll("_", " ")}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          {r.attribute === "custom" && (
            <Field label="Custom attribute name">
              <input
                required
                value={r.custom}
                placeholder="seats"
                onChange={(e) => update(i, { custom: e.target.value })}
              />
            </Field>
          )}
          {!presence(r.operator) && (
            <>
              {r.attribute === "custom" &&
                ["EQUALS", "NOT_EQUALS"].includes(r.operator) && (
                  <Field label="Operand type">
                    <Select
                      value={r.kind}
                      onChange={(e) =>
                        update(i, {
                          kind: e.target.value as Row["kind"],
                          input: e.target.value === "boolean" ? "true" : "",
                        })
                      }
                    >
                      <option>string</option>
                      <option>number</option>
                      <option>boolean</option>
                    </Select>
                  </Field>
                )}
              <Field
                label={list(r.operator) ? "Values (JSON array)" : "Operand"}
              >
                <input
                  value={r.input}
                  placeholder={
                    list(r.operator) ? '["IN", "US"]' : "Comparison value"
                  }
                  onChange={(e) => update(i, { input: e.target.value })}
                />
              </Field>
            </>
          )}
        </div>
      ))}
      {!disabled && value.rows.length < 32 && (
        <button
          type="button"
          onClick={() =>
            onChange({ ...value, rows: [...value.rows, ...draft().rows] })
          }
        >
          <Plus size={15} />
          Add condition
        </button>
      )}
    </fieldset>
  );
}
