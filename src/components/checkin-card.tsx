"use client";

import { useCallback, useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import type { CheckinStatus } from "@/lib/types";

type CheckinLabels = {
  title: string;
  hint: string;
  button: string;
  checkedIn: string;
  done: (data: { total: number; streak: number }) => string;
  error: string;
};

/**
 * 每日到馆打卡。
 * 服务端用 Redis BitMap 存「哪一天来过」，这里只负责展示与触发。
 */
export function CheckinCard({ labels }: { labels: CheckinLabels }) {
  const [status, setStatus] = useState<CheckinStatus | null>(null);
  const [busy, setBusy] = useState(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    apiFetch<CheckinStatus>("/api/checkin")
      .then((data) => {
        if (!cancelled) setStatus(data);
      })
      .catch(() => {
        // 打卡是附加功能，读失败就不展示，不影响地图
        if (!cancelled) setStatus({ todayChecked: false, totalDaysThisYear: 0, streakDays: 0 });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const handleCheckIn = useCallback(async () => {
    if (busy || status?.todayChecked) return;
    setBusy(true);
    setFailed(false);
    try {
      setStatus(await apiFetch<CheckinStatus>("/api/checkin", { method: "POST" }));
    } catch {
      setFailed(true);
    } finally {
      setBusy(false);
    }
  }, [busy, status?.todayChecked]);

  if (!status) return null;

  return (
    <section className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-[var(--border)] bg-black/20 px-4 py-3">
      <div className="min-w-0">
        <p className="text-xs uppercase tracking-[0.2em] text-[var(--ink-muted)]">{labels.title}</p>
        <p className="mt-1 text-sm">
          {status.todayChecked
            ? labels.done({ total: status.totalDaysThisYear, streak: status.streakDays })
            : labels.hint}
        </p>
        {failed ? <p className="mt-1 text-xs text-red-300">{labels.error}</p> : null}
      </div>
      <button
        type="button"
        onClick={() => void handleCheckIn()}
        disabled={busy || status.todayChecked}
        className="rounded-full border border-amber-300/60 bg-amber-400/10 px-4 py-1.5 text-sm text-amber-200 transition hover:bg-amber-400/20 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {status.todayChecked ? labels.checkedIn : busy ? "…" : labels.button}
      </button>
    </section>
  );
}
