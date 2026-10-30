/**
 * Numbers people type, by the app's rules (core/model NumberInput.kt): a decimal comma or
 * point ("62,5" = "62.5"), spaces around it, nothing else. The shared cases are in
 * core/model/fixtures/training-math.json.
 */
export type NumberInput = { kind: "blank" } | { kind: "invalid" } | { kind: "value"; value: number };

const DECIMAL = /^(\d+([.,]\d*)?|[.,]\d+)$/;

export function parseDecimal(text: string): NumberInput {
  const trimmed = text.trim();
  if (trimmed === "") return { kind: "blank" };
  if (!DECIMAL.test(trimmed)) return { kind: "invalid" };
  return { kind: "value", value: Number(trimmed.replace(",", ".")) };
}

/** [parseDecimal] as a number, or null for blank or invalid text. */
export function decimalOrNull(text: string): number | null {
  const parsed = parseDecimal(text);
  return parsed.kind === "value" ? parsed.value : null;
}
