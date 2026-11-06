#!/usr/bin/env node
// 把 JMeter 的 results.jtl（CSV）汇总成 markdown 表格
// 用法：node perf/summarize.mjs perf/results/results.jtl
import { readFileSync } from "node:fs";

const file = process.argv[2] ?? "perf/results/results.jtl";
const lines = readFileSync(file, "utf8").trim().split("\n");
const header = lines[0].split(",");
const idx = (name) => header.indexOf(name);
const iLabel = idx("label");
const iElapsed = idx("elapsed");
const iSuccess = idx("success");
const iTime = idx("timeStamp");

const groups = new Map();
let start = Infinity;
let end = 0;
for (const line of lines.slice(1)) {
  const cols = line.split(",");
  const label = cols[iLabel];
  const elapsed = Number(cols[iElapsed]);
  const success = cols[iSuccess] === "true";
  const ts = Number(cols[iTime]);
  start = Math.min(start, ts);
  end = Math.max(end, ts + elapsed);
  const g = groups.get(label) ?? { label, n: 0, errors: 0, times: [], start: Infinity, end: 0 };
  g.n += 1;
  g.start = Math.min(g.start, ts);
  g.end = Math.max(g.end, ts + elapsed);
  if (!success) g.errors += 1;
  g.times.push(elapsed);
  groups.set(label, g);
}

const pct = (sorted, p) => sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];
const seconds = (end - start) / 1000;

console.log("| 请求 | 样本数 | 错误率 | 平均 | P50 | P95 | P99 | 最大 | 吞吐 (req/s) |");
console.log("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |");
for (const g of groups.values()) {
  const sorted = g.times.sort((a, b) => a - b);
  const avg = sorted.reduce((a, b) => a + b, 0) / sorted.length;
  const rate = (g.n / ((g.end - g.start) / 1000)).toFixed(0);
  const errRate = ((g.errors / g.n) * 100).toFixed(2);
  console.log(
    `| ${g.label} | ${g.n} | ${errRate}% | ${avg.toFixed(1)} ms | ${pct(sorted, 50)} ms | ${pct(sorted, 95)} ms | ${pct(sorted, 99)} ms | ${sorted.at(-1)} ms | ${rate} |`
  );
}
console.log(`\n总样本 ${lines.length - 1}，耗时 ${seconds.toFixed(1)} 秒，整体吞吐 ${(lines.length - 1) / seconds | 0} req/s`);
