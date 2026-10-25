import { defineConfig } from "vitest/config";

// tsconfig keeps JSX as is for Next to compile; tests that render components need it compiled.
export default defineConfig({
  oxc: { jsx: { runtime: "automatic" } },
});
