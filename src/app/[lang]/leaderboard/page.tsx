"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AuthGate } from "@/components/auth-gate";
import { GameHeader } from "@/components/game-header";
import { apiFetch } from "@/lib/api";
import type { LeaderboardEntry } from "@/lib/types";
import { useGameStore } from "@/store/game-store";
import { flattenItems, useLevelCatalog } from "@/hooks/use-level-catalog";
import { useLocale } from "@/hooks/use-locale";
import { getTranslations } from "@/lib/translations";
import { withLocalePrefix } from "@/lib/i18n";

function formatDuration(seconds: number | null): string {
  if (seconds == null) return "-";
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return m > 0 ? `${m}m ${s}s` : `${s}s`;
}

export default function LeaderboardPage() {
  const router = useRouter();
  const locale = useLocale();
  const t = getTranslations(locale);
  const runStatus = useGameStore((state) => state.runStatus);
  const totalScore = useGameStore((state) => state.totalScore);
  const levelResults = useGameStore((state) => state.levelResults);
  const restart = useGameStore((state) => state.restart);
  const collectedItems = useGameStore((state) => state.collectedItems);
  const { levels } = useLevelCatalog(locale);

  const [entries, setEntries] = useState<LeaderboardEntry[]>([]);
  const [entriesLoading, setEntriesLoading] = useState(true);
  const [resetting, setResetting] = useState(false);

  useEffect(() => {
    let cancelled = false;
    apiFetch<LeaderboardEntry[]>("/api/leaderboard?limit=20")
      .then((data) => {
        if (!cancelled) setEntries(data);
      })
      .catch(() => {
        if (!cancelled) setEntries([]);
      })
      .finally(() => {
        if (!cancelled) setEntriesLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const completedCount = Object.values(levelResults).filter(
    (result) => result?.questionPassed && result?.qrPassed
  ).length;
  const total = levels.length || 12;
  const allCompleted = runStatus === "COMPLETED";

  const handleReset = async () => {
    if (resetting) return;
    setResetting(true);
    try {
      // 服务端把旧对局标记为 ABANDONED，再开一局新的
      await restart();
      router.push(withLocalePrefix("/map", locale));
    } catch {
      setResetting(false);
    }
  };

  const allItems = flattenItems(levels);

  return (
    <AuthGate>
      <div className="pb-8">
        <GameHeader
          title={t.leaderboard.title}
          subtitle={allCompleted ? t.leaderboard.subtitleComplete : t.leaderboard.subtitleIncomplete}
          showLeaderboardLink={false}
        />

        <main className="mx-auto mt-5 w-[min(980px,92vw)] space-y-4">
          <section className="treasure-panel p-5">
            <p className="text-xs uppercase tracking-[0.22em] text-[var(--ink-muted)]">{t.leaderboard.finalScore}</p>
            <h2 className="treasure-title mt-2 text-4xl text-amber-100">{totalScore}</h2>
            <p className="mt-2 text-sm text-[var(--ink-muted)]">
              {t.leaderboard.progress({ completed: completedCount, total })}
            </p>

            <div className="mt-4 flex flex-wrap gap-2">
              <Link
                href={withLocalePrefix("/map", locale)}
                className="rounded-xl border border-amber-500/50 bg-amber-500/20 px-4 py-2 text-sm text-amber-100 transition hover:bg-amber-500/30"
              >
                {t.leaderboard.backToMap}
              </Link>
              <button
                type="button"
                onClick={() => void handleReset()}
                disabled={resetting}
                className="rounded-xl border border-red-500/55 bg-red-500/20 px-4 py-2 text-sm text-red-100 transition hover:bg-red-500/30 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {t.leaderboard.reset}
              </button>
            </div>
          </section>

          {/* 服务端排行榜：Redis ZSET，MySQL 兜底 */}
          <section className="treasure-panel p-5">
            <h3 className="treasure-title text-xl">🏆 {t.leaderboard.title}</h3>
            {entriesLoading ? (
              <p className="mt-3 text-sm text-[var(--ink-muted)]">{t.leaderboard.loading}</p>
            ) : entries.length === 0 ? (
              <p className="mt-3 text-sm text-[var(--ink-muted)]">{t.leaderboard.empty}</p>
            ) : (
              <div className="mt-3 overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-[var(--border)] text-[10px] uppercase tracking-[0.15em] text-[var(--ink-muted)]">
                      <th className="pb-2 pr-3 font-medium">{t.leaderboard.rank}</th>
                      <th className="pb-2 pr-3 font-medium">{t.leaderboard.player}</th>
                      <th className="pb-2 pr-3 font-medium">{t.leaderboard.scoreUnit}</th>
                      <th className="pb-2 font-medium">{t.leaderboard.duration}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {entries.map((entry) => (
                      <tr key={`${entry.rank}-${entry.nickname}`} className="border-b border-[var(--border)]/50">
                        <td className="py-2.5 pr-3 text-[var(--ink-muted)]">#{entry.rank}</td>
                        <td className="py-2.5 pr-3">{entry.nickname}</td>
                        <td className="py-2.5 pr-3 font-semibold text-amber-300">{entry.totalScore}</td>
                        <td className="py-2.5 text-[var(--ink-muted)]">
                          {formatDuration(entry.durationSeconds)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <section className="treasure-panel p-5">
            <h3 className="treasure-title text-xl">{t.leaderboard.levelBreakdown}</h3>
            <div className="mt-3 space-y-2">
              {levels.map((level) => {
                const result = levelResults[level.level];
                const done = Boolean(result?.questionPassed && result?.qrPassed);
                return (
                  <div
                    key={level.level}
                    className="flex items-center justify-between rounded-xl border border-[var(--border)] bg-black/20 px-3 py-2 text-sm"
                  >
                    <span>
                      {t.level.title(level.level)} · {level.title}
                    </span>
                    <span className="text-[var(--ink-muted)]">
                      {done ? `${result?.score ?? 0} ${t.leaderboard.scoreUnit}` : t.leaderboard.notCompleted}
                    </span>
                  </div>
                );
              })}
            </div>
          </section>

          <section className="treasure-panel p-5">
            <h3 className="treasure-title text-xl">{t.leaderboard.itemSection}</h3>
            <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {allItems.map((item) => {
                const isCollected = collectedItems.includes(item.id);
                return (
                  <div
                    key={item.id}
                    className={`rounded-xl border border-[var(--border)] bg-black/20 p-3 ${
                      isCollected ? "" : "opacity-45"
                    }`}
                  >
                    <div className="aspect-[4/3] overflow-hidden rounded-lg border border-[var(--border)] bg-black/30">
                      <img src={item.imageUrl} alt={item.name} className="h-full w-full object-cover" />
                    </div>
                    <p className="treasure-title mt-2 text-base">{item.name}</p>
                    <p className="mt-1 text-xs text-[var(--ink-muted)]">
                      {isCollected ? item.description : t.items.locked}
                    </p>
                  </div>
                );
              })}
            </div>
          </section>
        </main>
      </div>
    </AuthGate>
  );
}
