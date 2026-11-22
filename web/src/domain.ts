import {
  parse,
  stringify,
  isLosslessNumber,
  LosslessNumber,
} from "lossless-json";
export type Role = "EMPLOYEE" | "ADMIN";
export type ValueType = "BOOLEAN" | "STRING" | "NUMBER" | "JSON";
export type Json =
  | null
  | boolean
  | string
  | number
  | LosslessNumber
  | Json[]
  | { [key: string]: Json };
export type User = { id: string; fullName: string; email: string };
export type Org = { id: string; name: string };
export type Project = Org & { key: string; organizationId: string };
export type Env = Org & {
  key: string;
  description: string | null;
  isDefault: boolean;
};
export type Flag = Org & {
  key: string;
  description: string | null;
  valueType: ValueType;
  defaultValue: Json;
  updatedAt: string;
};
export type Config = {
  flagId: string;
  environmentId: string;
  enabled: boolean;
  value: Json;
  updatedAt: string;
};
export type Condition = { attribute: string; operator: string; value?: Json };
export type Group = { match: "AND" | "OR"; conditions: Condition[] };
export type Rule = {
  action: "SERVE" | "DEFAULT";
  conditions?: Group;
  segmentKey?: string;
};
export type Segment = Org & {
  key: string;
  description: string | null;
  conditions: Group;
};
export type SdkKey = Org & {
  environmentKeys: string[];
  createdAt: string;
  revokedAt: string | null;
};
export const canEdit = (role: Role) => role === "ADMIN" || role === "EMPLOYEE";
export const canAdmin = (role: Role) => role === "ADMIN";
export const decode = (s: string) => parse(s) as Json;
export const encode = (v: unknown, pretty = false) =>
  stringify(v, null, pretty ? 2 : undefined) ?? "";
export const valueText = (t: ValueType, v: Json) =>
  t === "STRING" ? String(v) : encode(v, t === "JSON");
export function typed(t: ValueType, s: string): Json {
  if (t === "STRING") return s;
  let v: Json;
  try {
    v = decode(s);
  } catch {
    throw Error(
      "Enter a valid " +
        (t === "JSON" ? "JSON object or array" : t.toLowerCase()) +
        ".",
    );
  }
  if (t === "NUMBER" && !isLosslessNumber(v) && typeof v !== "number")
    throw Error("Enter a JSON number.");
  if (t === "BOOLEAN" && typeof v !== "boolean")
    throw Error("Choose true or false.");
  if (
    t === "JSON" &&
    (v === null || typeof v !== "object" || isLosslessNumber(v))
  )
    throw Error("Enter a JSON object or array.");
  return v;
}
export const initialValue: Record<ValueType, string> = {
  BOOLEAN: "false",
  NUMBER: "0",
  STRING: "",
  JSON: "{}",
};
export const firstCondition = (): Condition => ({
  attribute: "country",
  operator: "EQUALS",
  value: "",
});
export const firstGroup = (): Group => ({
  match: "AND",
  conditions: [firstCondition()],
});
export const errorText = (e: unknown) =>
  e instanceof Error ? e.message : "Request failed. Please try again.";
export function dateText(s: string) {
  return new Date(s).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}
export function envOrder(a: Env, b: Env) {
  const order = ["development", "staging", "production"];
  const rank = (e: Env) => (order.includes(e.key) ? order.indexOf(e.key) : 3);
  return rank(a) - rank(b) || a.name.localeCompare(b.name);
}
