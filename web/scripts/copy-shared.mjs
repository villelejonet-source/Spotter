// Files the web shares with the rest of the repository, copied (not imported) so the build
// only depends on web/:
// - The seeded exercise catalog ships with the Android app and is not synced, so the web
//   reads the same file.
// - The privacy policy is written once in docs/play-store/ and rendered at /privacy.
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const data = resolve(here, "../src/data");
mkdirSync(data, { recursive: true });

copyFileSync(resolve(here, "../../core/data/src/main/assets/exercises.json"), resolve(data, "exercises.json"));
console.log("Copied exercise catalog");

// Wrapped in JSON so pages and tests import it like the catalog, with no file reads at runtime.
const policy = readFileSync(resolve(here, "../../docs/play-store/privacy-policy.md"), "utf8");
writeFileSync(resolve(data, "privacy-policy.json"), `${JSON.stringify({ markdown: policy })}\n`);
console.log("Copied privacy policy");
