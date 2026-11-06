"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { TOTAL_MAX_SCORE } from "@/lib/constants";
import { apiFetch, ApiError } from "@/lib/api";
import { useGameStore } from "@/store/game-store";
import type { AdminStats, LeaderboardEntry } from "@/lib/types";
import { withLocalePrefix } from "@/lib/i18n";
import { DEFAULT_LOCALE } from "@/lib/i18n";

const calcAccuracy = (score: number) =>
  TOTAL_MAX_SCORE > 0 ? ((score / TOTAL_MAX_SCORE) * 100).toFixed(1) : "0.0";

function formatDuration(seconds: number | null): string {
  if (seconds == null) return "-";
  const total = Math.round(seconds);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return m > 0 ? `${m}m ${s}s` : `${s}s`;
}

export default function AdminPage() {
  const hydrated = useGameStore((state) => state.hasHydrated);
  const player = useGameStore((state) => state.player);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [stats, setStats] = useState<AdminStats | null>(null);
  const [leaderboard, setLeaderboard] = useState<LeaderboardEntry[]>([]);

  const isAdmin = player?.role === "ADMIN";

  const fetchData = useCallback(async () => {
    if (!isAdmin) return;
    setLoading(true);
    setError("");
    try {
      const [statsData, leaderboardData] = await Promise.all([
        apiFetch<AdminStats>("/api/admin/stats"),
        apiFetch<LeaderboardEntry[]>("/api/leaderboard?limit=100"),
      ]);
      setStats(statsData);
      setLeaderboard(leaderboardData);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.httpStatus === 403 ? "当前账号没有管理权限。" : err.message);
      } else {
        setError("获取数据失败，请稍后重试。");
      }
    } finally {
      setLoading(false);
    }
  }, [isAdmin]);

  useEffect(() => {
    void fetchData();
  }, [fetchData]);

  if (!hydrated) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[#0d0d0c] text-sm text-[var(--ink-muted)]">
        正在载入…
      </div>
    );
  }

  if (!player || !isAdmin) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-[#0d0d0c] px-6 text-center">
        <h1 className="treasure-title text-2xl text-amber-100">🔒 仅限管理员访问</h1>
        <p className="max-w-md text-sm text-[var(--ink-muted)]">
          数据看板需要管理员账号登录。请使用管理员学号登录后再访问本页面。
        </p>
        <Link
          href={withLocalePrefix("/login", DEFAULT_LOCALE)}
          className="rounded-xl border border-amber-500/50 bg-amber-500/20 px-4 py-2 text-sm text-amber-100 transition hover:bg-amber-500/30"
        >
          前往登录
        </Link>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#0d0d0c] px-4 py-8">
      <div className="mx-auto max-w-4xl space-y-5">
        {/* 頁頭 */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="treasure-title text-2xl text-amber-100">📊 游戏数据看板</h1>
            <p className="mt-1 text-xs text-[var(--ink-muted)]">
              图书馆寻宝游戏 · 通关记录（数据来自 MySQL，排行榜优先读 Redis）
            </p>
          </div>
          <button
            type="button"
            onClick={() => void fetchData()}
            disabled={loading}
            className="rounded-xl border border-amber-500/50 bg-amber-500/20 px-4 py-2 text-sm text-amber-100 transition hover:bg-amber-500/30 disabled:opacity-50"
          >
            {loading ? "加载中…" : "刷新"}
          </button>
        </div>

        {error ? (
          <div className="rounded-xl border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200">
            {error}
          </div>
        ) : null}

        {/* 統計卡片 */}
        {stats && (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <div className="treasure-panel p-4 text-center">
              <p className="text-[10px] uppercase tracking-[0.2em] text-[var(--ink-muted)]">注册玩家</p>
              <p className="treasure-title mt-1 text-3xl text-amber-100">{stats.playerCount}</p>
            </div>
            <div className="treasure-panel p-4 text-center">
              <p className="text-[10px] uppercase tracking-[0.2em] text-[var(--ink-muted)]">通关次数</p>
              <p className="treasure-title mt-1 text-3xl text-amber-100">{stats.totalCompletions}</p>
            </div>
            <div className="treasure-panel p-4 text-center">
              <p className="text-[10px] uppercase tracking-[0.2em] text-[var(--ink-muted)]">平均分</p>
              <p className="treasure-title mt-1 text-3xl text-amber-100">{stats.avgScore}</p>
              <p className="mt-1 text-[10px] text-[var(--ink-muted)]">
                平均正确率 {stats.avgScore > 0 ? `${calcAccuracy(stats.avgScore)}%` : "-"}
              </p>
            </div>
            <div className="treasure-panel p-4 text-center">
              <p className="text-[10px] uppercase tracking-[0.2em] text-[var(--ink-muted)]">平均耗时</p>
              <p className="treasure-title mt-1 text-3xl text-amber-100">
                {stats.avgDuration > 0 ? formatDuration(stats.avgDuration) : "-"}
              </p>
            </div>
          </div>
        )}

        {/* 排行榜 */}
        <div className="treasure-panel p-5">
          <h2 className="treasure-title text-xl text-amber-100">🏆 排行榜（每位玩家取最好一局）</h2>
          {leaderboard.length === 0 ? (
            <p className="mt-4 text-sm text-[var(--ink-muted)]">
              {loading ? "加载中…" : "暂无数据"}
            </p>
          ) : (
            <div className="mt-3 overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-[var(--border)] text-[10px] uppercase tracking-[0.15em] text-[var(--ink-muted)]">
                    <th className="pb-2 pr-3 font-medium">排名</th>
                    <th className="pb-2 pr-3 font-medium">玩家</th>
                    <th className="pb-2 pr-3 font-medium">分数</th>
                    <th className="pb-2 pr-3 font-medium">正确率</th>
                    <th className="pb-2 font-medium">耗时</th>
                  </tr>
                </thead>
                <tbody>
                  {leaderboard.map((row) => (
                    <tr key={`${row.rank}-${row.nickname}`} className="border-b border-[var(--border)]/50 text-amber-100">
                      <td className="py-2.5 pr-3 text-[var(--ink-muted)]">#{row.rank}</td>
                      <td className="py-2.5 pr-3">{row.nickname}</td>
                      <td className="py-2.5 pr-3">{row.totalScore}</td>
                      <td className="py-2.5 pr-3">{calcAccuracy(row.totalScore)}%</td>
                      <td className="py-2.5 text-[var(--ink-muted)]">
                        {formatDuration(row.durationSeconds)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
