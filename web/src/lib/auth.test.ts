import { describe, expect, it } from "vitest";
import { authErrorMessage, passwordProblem, safeNext } from "./auth";

describe("safeNext", () => {
  it("keeps same-site paths", () => {
    expect(safeNext("/reset-password")).toBe("/reset-password");
    expect(safeNext("/plans/abc?x=1")).toBe("/plans/abc?x=1");
  });

  it("refuses anything that could leave the site", () => {
    for (const bad of ["https://evil.example", "//evil.example", "/\\evil.example", "javascript:alert(1)", "", null, undefined]) {
      expect(safeNext(bad)).toBe("/");
    }
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
  it("puts Supabase's codes in plain words and passes others through", () => {
    expect(authErrorMessage({ code: "invalid_credentials", message: "Invalid login credentials" })).toBe("Wrong email or password.");
    expect(authErrorMessage({ code: "something_new", message: "Raw message" })).toBe("Raw message");
  });
});
