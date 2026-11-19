// Parses every ```mermaid block under the given Markdown files with the real Mermaid parser.
import { createRequire } from "node:module";
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import path from "node:path";

const depsDir = process.argv[2];
const files = process.argv.slice(3);
const require = createRequire(path.join(depsDir, "package.json"));
const { JSDOM } = require("jsdom");
const dom = new JSDOM("<!doctype html><html><body></body></html>");
globalThis.window = dom.window;
globalThis.document = dom.window.document;
globalThis.DOMParser = dom.window.DOMParser;
globalThis.Element = dom.window.Element;
const mermaid = (await import(pathToFileURL(require.resolve("mermaid/dist/mermaid.core.mjs")).href)).default;
mermaid.initialize({ startOnLoad: false });

let failures = 0;
for (const file of files) {
  const text = readFileSync(file, "utf8").replace(/\r\n/g, "\n");
  const blocks = [...text.matchAll(/```mermaid\n([\s\S]*?)```/g)].map((m) => m[1]);
  for (const [i, block] of blocks.entries()) {
    try {
      await mermaid.parse(block);
      console.log(`OK    ${file}#${i}`);
    } catch (error) {
      failures++;
      console.log(`FAIL  ${file}#${i}: ${String(error.message || error).split("\n").slice(0, 4).join(" | ")}`);
    }
  }
}
process.exit(failures ? 1 : 0);
