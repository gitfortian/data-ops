import { spawnSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../", import.meta.url));
const result = spawnSync(
  process.execPath,
  ["node_modules/typescript/bin/tsc", "--noEmit", "--pretty", "false"],
  { cwd: root, encoding: "utf8", maxBuffer: 16 * 1024 * 1024 }
);
if (result.error || ![0, 2].includes(result.status))
  throw (
    result.error ??
    new Error(result.stderr || `TypeScript exited ${result.status}`)
  );
const allowed = JSON.parse(
  readFileSync(new URL("./type-baseline.json", import.meta.url), "utf8")
);
const actual = {};
for (const diagnostic of result.stdout.split(
  /(?=^[^\s].*\(\d+,\d+\): error TS)/m
)) {
  if (!diagnostic.trim()) continue;
  const key = diagnostic
    .replace(/\(\d+,\d+\): error /, ": error ")
    .trim()
    .replace(/\r/g, "")
    .replaceAll("\\", "/")
    .replaceAll(root.replaceAll("\\", "/"), "<ui>/");
  actual[key] = (actual[key] ?? 0) + 1;
}
const regressions = Object.entries(actual).filter(
  ([key, count]) => count > (allowed[key] ?? 0)
);
if (regressions.length) {
  console.error(
    regressions
      .map(([key, count]) => `${count - (allowed[key] ?? 0)} new: ${key}`)
      .join("\n")
  );
  process.exitCode = 1;
} else {
  if (process.argv.includes("--prune"))
    writeFileSync(
      new URL("./type-baseline.json", import.meta.url),
      JSON.stringify(actual, null, 2) + "\n"
    );
  console.log(
    `TypeScript debt gate passed (${Object.values(actual).reduce(
      (a, b) => a + b,
      0
    )} existing diagnostics).`
  );
}
