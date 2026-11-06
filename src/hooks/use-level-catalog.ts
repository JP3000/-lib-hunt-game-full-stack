"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import type { Locale } from "@/lib/i18n";
import type { ItemConfig, LevelSummary } from "@/lib/types";

/**
 * 关卡目录（标题 + 每关道具），数据来自服务端 `/api/levels`。
 *
 * 做了一层模块级缓存：地图 / 道具面板 / 结算页 / 积分板会在同一页面生命周期里
 * 多次使用它，只应该请求一次。切语言时按 locale 分别缓存。
 */
const cache = new Map<Locale, LevelSummary[]>();
const inflight = new Map<Locale, Promise<LevelSummary[]>>();

function fetchCatalog(locale: Locale): Promise<LevelSummary[]> {
  const existing = inflight.get(locale);
  if (existing) return existing;

  const request = apiFetch<LevelSummary[]>(
    `/api/levels?locale=${encodeURIComponent(locale)}`
  )
    .then((levels) => {
      cache.set(locale, levels);
      return levels;
    })
    .finally(() => {
      inflight.delete(locale);
    });

  inflight.set(locale, request);
  return request;
}

export function useLevelCatalog(locale: Locale) {
  const [levels, setLevels] = useState<LevelSummary[]>(() => cache.get(locale) ?? []);
  const [loading, setLoading] = useState(() => !cache.has(locale));

  useEffect(() => {
    let cancelled = false;

    const cached = cache.get(locale);
    if (cached) {
      setLevels(cached);
      setLoading(false);
      return;
    }

    setLoading(true);
    fetchCatalog(locale)
      .then((data) => {
        if (!cancelled) setLevels(data);
      })
      .catch(() => {
        if (!cancelled) setLevels([]);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [locale]);

  return { levels, loading };
}

/** 把每一关的道具摊平成道具墙用的列表（同一道具只保留一次） */
export function flattenItems(levels: LevelSummary[]): ItemConfig[] {
  const seen = new Set<string>();
  const items: ItemConfig[] = [];
  for (const level of levels) {
    for (const item of level.items) {
      if (seen.has(item.id)) continue;
      seen.add(item.id);
      items.push(item);
    }
  }
  return items;
}
