// Renders each ```mermaid block to PNG in a real browser using the local Mermaid bundle.
import { createRequire } from "node:module";
import { readFileSync, mkdirSync } from "node:fs";
import path from "node:path";

const [depsDir, browserPath, outDir, ...files] = process.argv.slice(2);
const require = createRequire(path.join(depsDir, "package.json"));
const puppeteer = require("puppeteer-core");
const mermaidJs = readFileSync(require.resolve("mermaid/dist/mermaid.min.js"), "utf8");
mkdirSync(outDir, { recursive: true });

const browser = await puppeteer.launch({ executablePath: browserPath, headless: true, args: ["--no-sandbox", "--user-data-dir=" + path.join(outDir, ".profile"), "--no-first-run", "--disable-gpu"] });
const page = await browser.newPage();
await page.setViewport({ width: 1400, height: 900, deviceScaleFactor: 1 });
await page.setContent("<!doctype html><html><body style='margin:0;background:#fff'><div id='out'></div></body></html>");
await page.addScriptTag({ content: mermaidJs });
await page.evaluate(() => window.mermaid.initialize({ startOnLoad: false, theme: "default" }));

let failures = 0;
for (const file of files) {
  const text = readFileSync(file, "utf8").replace(/\r\n/g, "\n");
  const blocks = [...text.matchAll(/```mermaid\n([\s\S]*?)```/g)].map((m) => m[1]);
  for (const [i, block] of blocks.entries()) {
    const name = file.replace(/[\\/]/g, "_").replace(/\.md$/, "") + (blocks.length > 1 ? `_${i}` : "");
    const result = await page.evaluate(async (code, id) => {
      try {
        const { svg } = await window.mermaid.render(id, code);
        document.getElementById("out").innerHTML = svg;
        const el = document.querySelector("#out svg");
        const box = el.getBoundingClientRect();
        const texts = [...el.querySelectorAll("text, foreignObject")].map((n) => n.textContent.trim()).filter(Boolean);
        return { ok: true, width: box.width, height: box.height, labels: texts.length, error: svg.includes("Syntax error") };
      } catch (error) {
        return { ok: false, message: String(error.message || error) };
      }
    }, block, "d" + Math.random().toString(36).slice(2));
    if (!result.ok || result.error) {
      failures++;
      console.log(`FAIL  ${name}: ${result.message || "syntax error svg"}`);
      continue;
    }
    const el = await page.$("#out svg");
    await el.screenshot({ path: path.join(outDir, name + ".png") });
    console.log(`OK    ${name}  ${Math.round(result.width)}x${Math.round(result.height)}  labels=${result.labels}`);
  }
}
await browser.close();
process.exit(failures ? 1 : 0);
