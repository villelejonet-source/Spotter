import { describe, expect, it } from "vitest";
import {
  authErrorMessage,
  GENERIC_AUTH_ERROR,
  parseEmailCode,
  passwordProblem,
  RECOVERY_COOKIE_MAX_AGE,
  recoveryCookieOptions,
  safeNext,
} from "./auth";

describe("parseEmailCode", () => {
  const ok = { email: " runner@example.com ", token: "123456", type: "recovery" };

  it("accepts a well-formed code and trims the email", () => {
    expect(parseEmailCode(ok)).toEqual({ email: "runner@example.com", token: "123456", type: "recovery" });
    expect(parseEmailCode({ ...ok, token: "12345678", type: "signup" })?.type).toBe("signup");
    expect(parseEmailCode({ ...ok, type: "email" })?.type).toBe("email");
  });

  it("refuses anything else", () => {
    for (const bad of [
      null,
      "x",
      { ...ok, type: "invite" },
      { ...ok, type: "magiclink" },
      { ...ok, token: "12345" },
      { ...ok, token: "12345678901" },
      { ...ok, token: "12a456" },
      { ...ok, token: 123456 },
      { ...ok, email: "not-an-email" },
      { ...ok, email: `${"a".repeat(320)}@x.y` },
    ]) {
      expect(parseEmailCode(bad), JSON.stringify(bad)).toBeNull();
    }
  });
});

describe("recoveryCookieOptions", () => {
  it("is httpOnly, lax, site-wide and short-lived", () => {
    expect(recoveryCookieOptions(true)).toEqual({ httpOnly: true, sameSite: "lax", secure: true, path: "/", maxAge: RECOVERY_COOKIE_MAX_AGE });
    expect(recoveryCookieOptions(false).secure).toBe(false);
    expect(RECOVERY_COOKIE_MAX_AGE).toBe(15 * 60);
  });
});

describe("safeNext", () => {
  it("keeps same-site paths", () => {
    expect(safeNext("/reset-password")).toBe("/reset-password");
    expect(safeNext("/plans/abc?x=1")).toBe("/plans/abc?x=1");
    expect(safeNext("/history/2026-10#top")).toBe("/history/2026-10#top");
    expect(safeNext("/")).toBe("/");
  });

  it("refuses anything that could leave the site", () => {
    for (const bad of ["https://evil.example", "//evil.example", "/\\evil.example", "javascript:alert(1)", "", null, undefined]) {
      expect(safeNext(bad)).toBe("/");
    }
  });

  it("refuses control characters and backslashes anywhere, not just at the start", () => {
    for (const bad of ["/\t/evil.example", "/\n/evil.example", "/\r\n/evil.example", "/\\\\evil.example", "/plans\\evil.example", "/plans/\x00", "/plans\x7f", "/a\u0001b"]) {
      expect(safeNext(bad), JSON.stringify(bad)).toBe("/");
    }
  });

  it("uses the given fallback", () => {
    expect(safeNext("//evil.example", "/plans")).toBe("/plans");
  });
});

describe("passwordProblem", () => {
  it("needs 8 characters and a matching repeat", () => {
    expect(passwordProblem("short", "short")).toMatch(/at least 8/);
    expect(passwordProblem("longenough", "different1")).toMatch(/don't match/);
    expect(passwordProblem("longenough", "longenough")).toBeNull();
  });
});

describe("authErrorMessage", () => {
  it("puts Supabase's codes in plain words", () => {
    expect(authErrorMessage({ code: "invalid_credentials", message: "Invalid login credentials" })).toBe("Wrong email or password.");
    expect(authErrorMessage({ code: "email_exists", message: "x" })).toMatch(/already exists/);
  });

  it("never shows the raw message for a code it doesn't know", () => {
    expect(authErrorMessage({ code: "something_new", message: "Raw message" })).toBe(GENERIC_AUTH_ERROR);
    expect(authErrorMessage({ message: 'relation "auth.users" does not exist' })).toBe(GENERIC_AUTH_ERROR);
    expect(GENERIC_AUTH_ERROR).toBe("Something went wrong. Try again.");
  });
});
