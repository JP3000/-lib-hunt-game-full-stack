#!/usr/bin/env node
/**
 * 把前端 src/data/levels.ts 的三语关卡内容导出为 SQL 种子数据。
 *
 * 用法：
 *   node --experimental-strip-types scripts/export-levels.mjs
 *   node --experimental-strip-types scripts/export-levels.mjs V5__update_levels.sql
 *
 * 产出：
 *   server/src/main/resources/db/migration/<文件名，默认 V3__seed_levels.sql>
 *
 * 注意：Flyway 会校验已执行脚本的 checksum。V3 一旦在某个环境跑过，
 * 就不能原地改它——改内容后请生成一个新版本号的迁移文件（或用 UPDATE 脚本手工更新）。
 *
 * 设计：
 *   - 语言无关字段（二维码答案、正确选项、视频/图片地址）只存一份，
 *     导出时若三种语言不一致会直接报错，避免静默丢失。
 *   - 三语文案存成 JSON 列，键为 locale。
 */
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const TMP = path.join(os.tmpdir(), "libhunt-levelgen");
const OUT_NAME = process.argv[2] ?? "V3__seed_levels.sql";
if (!/^V\d+__.+\.sql$/.test(OUT_NAME)) {
  throw new Error(`输出文件名需符合 Flyway 命名（V<版本号>__<描述>.sql），收到：${OUT_NAME}`);
}
const OUT = path.join(ROOT, "server/src/main/resources/db/migration", OUT_NAME);
const LOCALES = ["zh-Hant", "zh-Hans", "en"];
const DEFAULT_LOCALE = "zh-Hant";
const TOTAL_LEVELS = 12;

/* ------------------------------------------------------------------ */
/* 1. 在临时目录里复刻一份可被 Node 直接执行的 levels.ts                */
/* ------------------------------------------------------------------ */

async function loadLevels() {
  await fs.rm(TMP, { recursive: true, force: true });
  await fs.mkdir(TMP, { recursive: true });

  const src = await fs.readFile(path.join(ROOT, "src/data/levels.ts"), "utf8");
  const rewritten = src
    .replaceAll('from "@/lib/i18n"', 'from "./i18n.ts"')
    .replaceAll('from "@/lib/constants"', 'from "./constants.ts"')
    .replaceAll('from "@/lib/types"', 'from "./types.ts"');

  await fs.writeFile(path.join(TMP, "levels.ts"), rewritten);
  await fs.writeFile(
    path.join(TMP, "i18n.ts"),
    `export type Locale = "zh-Hant" | "zh-Hans" | "en";\n` +
      `export const DEFAULT_LOCALE: Locale = "zh-Hant";\n`
  );
  await fs.writeFile(
    path.join(TMP, "constants.ts"),
    `export const TOTAL_LEVELS = 12;\n` +
      `const C = "20250710";\n` +
      `export const LEVEL1_VIDEO_URL = \`/video/level1.mp4?v=\${C}\`;\n` +
      `export const LEVEL4_VIDEO_URL = \`/video/level4-fushong.mp4?v=\${C}\`;\n` +
      `export const LEVEL7_VIDEO_URL = \`/video/level7-coin.mp4?v=\${C}\`;\n` +
      `export const LEVEL9_VIDEO_URL = \`/video/level9-treasure.mp4?v=\${C}\`;\n` +
      `export const FINALE_VIDEO_URL = \`/video/finale-pentagon.mp4?v=\${C}\`;\n`
  );
  await fs.writeFile(path.join(TMP, "types.ts"), `export type LevelConfig = any;\n`);

  const mod = await import(pathToFileURL(path.join(TMP, "levels.ts")).href);
  return Object.fromEntries(LOCALES.map((locale) => [locale, mod.getLevels(locale)]));
}

/* ------------------------------------------------------------------ */
/* 2. 校验 & 聚合                                                      */
/* ------------------------------------------------------------------ */

function fail(message) {
  throw new Error(`内容校验失败：${message}`);
}

async function aggregate(levelsByLocale) {
  for (const locale of LOCALES) {
    if (levelsByLocale[locale].length !== TOTAL_LEVELS) {
      fail(`${locale} 关卡数为 ${levelsByLocale[locale].length}，应为 ${TOTAL_LEVELS}`);
    }
  }

  const levels = new Map();
  const items = new Map();

  for (const locale of LOCALES) {
    for (const level of levelsByLocale[locale]) {
      let agg = levels.get(level.level);
      if (!agg) {
        agg = {
          levelNo: level.level,
          title: {},
          story: {},
          completionStory: {},
          question: {},
          qrLocation: {},
          qrLocationNote: {},
          storyImageAlt: {},
          storyImageCaption: {},
          correctOptionKeys: null,
          qrAnswer: null,
          videoUrl: null,
          storyImageUrl: null,
          options: new Map(),
          itemKeys: [],
        };
        levels.set(level.level, agg);
      }

      // 语言无关字段必须三语一致
      if (agg.qrAnswer !== null && agg.qrAnswer !== level.qrAnswer) {
        fail(`第 ${level.level} 关 qrAnswer 在不同语言下不一致`);
      }
      agg.qrAnswer = level.qrAnswer;

      const keys = JSON.stringify([...level.correctOptionIds].sort());
      if (agg.correctOptionKeys !== null && agg.correctOptionKeys !== keys) {
        fail(`第 ${level.level} 关 correctOptionIds 在不同语言下不一致`);
      }
      agg.correctOptionKeys = keys;

      if (agg.videoUrl && agg.videoUrl !== (level.videoUrl ?? null)) {
        fail(`第 ${level.level} 关 videoUrl 在不同语言下不一致`);
      }
      agg.videoUrl = level.videoUrl ?? null;

      if (agg.storyImageUrl && agg.storyImageUrl !== (level.storyImageUrl ?? null)) {
        fail(`第 ${level.level} 关 storyImageUrl 在不同语言下不一致`);
      }
      agg.storyImageUrl = level.storyImageUrl ?? null;

      // 文案
      if (level.completionStory) agg.completionStory[locale] = level.completionStory;
      if (level.qrLocation) agg.qrLocation[locale] = level.qrLocation;
      if (level.qrLocationNote) agg.qrLocationNote[locale] = level.qrLocationNote;
      if (level.storyImageAlt) agg.storyImageAlt[locale] = level.storyImageAlt;
      if (level.storyImageCaption) agg.storyImageCaption[locale] = level.storyImageCaption;
      agg.title[locale] = level.title;
      agg.story[locale] = level.story;
      agg.question[locale] = level.question;

      // 选项
      for (const [index, option] of level.options.entries()) {
        let opt = agg.options.get(option.id);
        if (!opt) {
          opt = { key: option.id, label: {}, order: index + 1 };
          agg.options.set(option.id, opt);
        }
        opt.label[locale] = option.label;
      }

      // 道具
      for (const [index, item] of level.items.entries()) {
        let it = items.get(item.id);
        if (!it) {
          it = { key: item.id, name: {}, description: {}, imageUrl: null, levels: new Set() };
          items.set(item.id, it);
        }
        if (it.imageUrl && it.imageUrl !== item.imageUrl) {
          fail(`道具 ${item.id} 在不同位置 imageUrl 不一致`);
        }
        it.imageUrl = item.imageUrl;
        it.name[locale] = item.name;
        it.description[locale] = item.description;
        it.levels.add(level.level);
        if (!agg.itemKeys.includes(item.id)) agg.itemKeys.push(item.id);
        if (!it.order) it.order = index + 1;
      }
    }
  }

  // 所有语言的选项集合必须一致
  for (const [levelNo, agg] of levels) {
    for (const locale of LOCALES) {
      const expected = [...agg.options.keys()].sort().join(",");
      const actual = [...levelsByLocale[locale]
        .find((l) => l.level === levelNo)
        .options.map((o) => o.id)].sort().join(",");
      if (expected !== actual) fail(`第 ${levelNo} 关选项集合在 ${locale} 下不一致`);
    }
  }

  return { levels, items };
}

/* ------------------------------------------------------------------ */
/* 3. 生成 SQL                                                         */
/* ------------------------------------------------------------------ */

const sqlString = (value) =>
  `'${String(value).replaceAll("\\", "\\\\").replaceAll("'", "''")}'`;

/** 生成 JSON 列的字面量；空对象生成 NULL */
const sqlJson = (value, { nullable = true } = {}) => {
  if (!value || Object.keys(value).length === 0) {
    if (nullable) return "NULL";
    return sqlString(JSON.stringify({}));
  }
  return sqlString(JSON.stringify(value));
};

function buildSql({ levels, items }) {
  const orderedLevels = [...levels.values()].sort((a, b) => a.levelNo - b.levelNo);
  const orderedItems = [...items.values()].sort((a, b) => a.key.localeCompare(b.key));

  const lines = [];
  lines.push("-- 由 scripts/export-levels.mjs 自动生成，请勿手改。");
  lines.push("-- 重新生成：node --experimental-strip-types scripts/export-levels.mjs");
  lines.push("");
  lines.push("SET NAMES utf8mb4;");
  lines.push("");

  /* level */
  lines.push("INSERT INTO level");
  lines.push("  (level_no, title, story, completion_story, question, correct_option_keys,");
  lines.push("   qr_answer, qr_location, qr_location_note, video_url, story_image_url,");
  lines.push("   story_image_alt, story_image_caption)");
  lines.push("VALUES");
  lines.push(
    orderedLevels
      .map(
        (lv) =>
          `  (${lv.levelNo}, ${sqlJson(lv.title, { nullable: false })}, ` +
          `${sqlJson(lv.story, { nullable: false })}, ${sqlJson(lv.completionStory)}, ` +
          `${sqlJson(lv.question, { nullable: false })}, ${sqlString(lv.correctOptionKeys)}, ` +
          `${sqlString(lv.qrAnswer)}, ${sqlJson(lv.qrLocation)}, ${sqlJson(lv.qrLocationNote)}, ` +
          `${lv.videoUrl ? sqlString(lv.videoUrl) : "NULL"}, ` +
          `${lv.storyImageUrl ? sqlString(lv.storyImageUrl) : "NULL"}, ` +
          `${sqlJson(lv.storyImageAlt)}, ${sqlJson(lv.storyImageCaption)})`
      )
      .join(",\n")
  );
  lines.push(";");
  lines.push("");

  /* level_option */
  lines.push("INSERT INTO level_option (level_id, option_key, label, sort_order) VALUES");
  const optionRows = [];
  for (const lv of orderedLevels) {
    const options = [...lv.options.values()].sort((a, b) => a.order - b.order);
    for (const opt of options) {
      optionRows.push(
        `  ((SELECT id FROM level WHERE level_no = ${lv.levelNo}), ` +
          `${sqlString(opt.key)}, ${sqlJson(opt.label, { nullable: false })}, ${opt.order})`
      );
    }
  }
  lines.push(optionRows.join(",\n"));
  lines.push(";");
  lines.push("");

  /* item */
  lines.push("INSERT INTO item (item_key, name, description, image_url) VALUES");
  lines.push(
    orderedItems
      .map(
        (it) =>
          `  (${sqlString(it.key)}, ${sqlJson(it.name, { nullable: false })}, ` +
          `${sqlJson(it.description, { nullable: false })}, ${sqlString(it.imageUrl)})`
      )
      .join(",\n")
  );
  lines.push(";");
  lines.push("");

  /* level_item */
  lines.push("INSERT INTO level_item (level_id, item_id, sort_order) VALUES");
  const linkRows = [];
  for (const lv of orderedLevels) {
    for (const [index, itemKey] of lv.itemKeys.entries()) {
      linkRows.push(
        `  ((SELECT id FROM level WHERE level_no = ${lv.levelNo}), ` +
          `(SELECT id FROM item WHERE item_key = ${sqlString(itemKey)}), ${index + 1})`
      );
    }
  }
  lines.push(linkRows.join(",\n"));
  lines.push(";");
  lines.push("");

  return lines.join("\n");
}

/* ------------------------------------------------------------------ */

const levelsByLocale = await loadLevels();
const data = await aggregate(levelsByLocale);
const sql = buildSql(data);
await fs.writeFile(OUT, sql, "utf8");

const optionCount = [...data.levels.values()].reduce((n, lv) => n + lv.options.size, 0);
const linkCount = [...data.levels.values()].reduce((n, lv) => n + lv.itemKeys.length, 0);
console.log(`已生成 ${path.relative(ROOT, OUT)}`);
console.log(
  `  levels=${data.levels.size} options=${optionCount} items=${data.items.size} level_items=${linkCount}`
);
