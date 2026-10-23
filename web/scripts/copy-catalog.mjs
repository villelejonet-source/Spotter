// The seeded exercise catalog ships with the Android app and is not synced, so the
// web reads the same file. Copied (not imported) so the build only depends on web/.
import { copyFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const source = resolve(here, "../../core/data/src/main/assets/exercises.json");
const target = resolve(here, "../src/data/exercises.json");
mkdirSync(dirname(target), { recursive: true });
copyFileSync(source, target);
console.log("Copied exercise catalog");
