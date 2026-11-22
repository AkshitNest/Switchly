import test from "node:test";
import assert from "node:assert/strict";
import {
  typed,
  valueText,
  encode,
  decode,
  canAdmin,
  canEdit,
} from "../src/domain.ts";
test("typed values preserve exact decimal and nested integer literals", () => {
  const decimal = "0.12345678901234567890123456789";
  assert.equal(valueText("NUMBER", typed("NUMBER", decimal)), decimal);
  assert.equal(
    encode(typed("JSON", '{"limit":90071992547409931234}')),
    '{"limit":90071992547409931234}',
  );
  assert.equal(typed("BOOLEAN", "false"), false);
  assert.equal(typed("STRING", "false"), "false");
  assert.equal(typed("STRING", ""), "");
});
test("incompatible flag values fail before submission", () => {
  for (const [type, value] of [
    ["NUMBER", '"1"'],
    ["JSON", "null"],
    ["JSON", "2"],
    ["BOOLEAN", "1"],
    ["NUMBER", ""],
  ])
    assert.throws(() => typed(type, value));
});
test("role hierarchy matches management access", () => {
  for (const [role, edit, admin] of [
    ["ADMIN", true, true],
    ["EMPLOYEE", true, false],
    ["UNKNOWN", false, false],
  ]) {
    assert.equal(canEdit(role), edit);
    assert.equal(canAdmin(role), admin);
  }
});
