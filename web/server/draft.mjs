// Only the operator's release brief reaches the model. The model has no credentials or tools.
const properties = {
  name: { type: "string", minLength: 1, maxLength: 120 },
  key: { type: "string", pattern: "^[a-z0-9][a-z0-9-]{0,99}$" },
  description: { type: "string", maxLength: 500 },
  valueType: { type: "string", enum: ["BOOLEAN", "STRING", "NUMBER", "JSON"] },
  defaultText: { type: "string", maxLength: 1000 },
};
const schema = { type: "object", properties, required: Object.keys(properties), additionalProperties: false };
const system = "Draft one Switchly feature flag. Use a short readable name and lowercase hyphenated key. The default is the safe fallback before release. Use BOOLEAN and false for a toggle unless requested otherwise. Encode the editable default in defaultText: true or false for BOOLEAN, decimal digits for NUMBER, plain text for STRING, valid object or array text for JSON. Describe only the requested feature. Return this schema: " + JSON.stringify(schema);
const failure = (status, message) => Response.json({ error: { code: "FLAG_DRAFT", message } }, { status });
let active = 0;

export async function draftFlag(body, fetcher = fetch) {
  let brief;
  try { brief = JSON.parse(body.toString()).brief; } catch { /* validated below */ }
  if (typeof brief !== "string" || !brief.trim() || brief.length > 1500)
    return failure(400, "Describe the feature in 1–1500 characters.");
  if (active >= 2) return failure(429, "The flag assistant is busy. Try again shortly.");
  active++;
  try {
    const response = await fetcher(new URL("/api/chat", process.env.OLLAMA_ORIGIN || "http://127.0.0.1:11434"), {
      method: "POST", redirect: "error", signal: AbortSignal.timeout(90_000),
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        model: process.env.OLLAMA_MODEL || "qwen:latest", stream: false, format: schema,
        messages: [{ role: "system", content: system }, { role: "user", content: brief.trim() }],
        options: { temperature: 0, num_predict: 400 },
      }),
    });
    if (!response.ok) return failure(503, "The flag model is unavailable. Check the workspace Ollama service and model.");
    const draft = JSON.parse((await response.json()).message.content);
    if (!Object.entries(properties).every(([key, rule]) => typeof draft[key] === "string" &&
        (!rule.maxLength || draft[key].length <= rule.maxLength)) ||
        !draft.name.trim() || !new RegExp(properties.key.pattern).test(draft.key) ||
        !properties.valueType.enum.includes(draft.valueType)) throw Error();
    if (draft.valueType !== "STRING") {
      const value = JSON.parse(draft.defaultText);
      const kind = { BOOLEAN: "boolean", NUMBER: "number", JSON: "object" }[draft.valueType];
      if (value === null || typeof value !== kind) throw Error();
    }
    return Response.json(Object.fromEntries(Object.keys(properties).map(k => [k, draft[k]])));
  } catch (e) {
    return failure(503, e instanceof TypeError || e?.name === "TimeoutError"
      ? "Could not reach the local flag model. Try again or fill in the flag manually."
      : "The model returned an invalid draft. Revise the brief and try again.");
  } finally { active--; }
}
