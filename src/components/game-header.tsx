"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { useGameStore } from "@/store/game-store";
import { ItemPanel } from "@/components/item-panel";
import { useLocale } from "@/hooks/use-locale";
import { getTranslations } from "@/lib/translations";
import { LOCALES, replaceLocaleInPathname, withLocalePrefix } from "@/lib/i18n";

type GameHeaderProps = {
  title: string;
  subtitle?: string;
  showMapLink?: boolean;
  showLeaderboardLink?: boolean;
};

export function GameHeader({
  title,
  subtitle,
  showMapLink = true,
  showLeaderboardLink = true,
}: GameHeaderProps) {
  const router = useRouter();
  const pathname = usePathname() ?? "/";
  const locale = useLocale();
  const t = getTranslations(locale);
  const studentId = useGameStore((state) => state.studentId);
  const totalScore = useGameStore((state) => state.totalScore);
  const collectedItems = useGameStore((state) => state.collectedItems);
  const logout = useGameStore((state) => state.logout);
  const [showItems, setShowItems] = useState(false);

  const handleLogout = () => {
    logout();
    router.replace(withLocalePrefix("/login", locale));
  };

  return (
    <header className="treasure-panel mx-auto flex w-[min(980px,92vw)] flex-col gap-3 px-4 py-4 md:flex-row md:items-center md:justify-between">
      <div>
        <p className="text-xs uppercase tracking-[0.22em] text-[var(--ink-muted)]">
          {t.brand}
        </p>
        <h1 className="treasure-title text-xl text-[var(--ink-main)] md:text-2xl">{title}</h1>
        {subtitle ? <p className="mt-1 text-sm text-[var(--ink-muted)]">{subtitle}</p> : null}
      </div>

      <div className="flex w-full flex-col gap-2 text-sm md:w-auto md:items-end">
        <div className="flex flex-wrap items-center gap-2">
          <span className="rounded-full border border-[var(--border)] px-3 py-1 text-[var(--ink-muted)]">
            {t.header.player}: {studentId}
          </span>
          <span className="rounded-full border border-[var(--border)] px-3 py-1 text-[var(--ink-main)]">
            {t.header.score}: {totalScore}
          </span>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div className="flex items-center gap-1 rounded-full border border-[var(--border)] bg-black/15 px-2 py-1">
            <span className="text-xs text-[var(--ink-muted)]">{t.header.language}</span>
            {LOCALES.map((lang) => (
              <Link
                key={lang}
                href={replaceLocaleInPathname(pathname, lang)}
                className={`rounded-full px-2 py-0.5 text-xs transition ${
                  lang === locale
                    ? "bg-amber-500/30 text-amber-100"
                    : "text-[var(--ink-main)] hover:bg-black/20"
                }`}
              >
                {lang === "zh-Hant" ? "繁" : lang === "zh-Hans" ? "简" : "EN"}
              </Link>
            ))}
          </div>
          <button
            type="button"
            onClick={handleLogout}
            className="rounded-full border border-amber-500/40 bg-amber-500/20 px-3 py-1 text-amber-100 transition hover:bg-amber-500/30"
          >
            {t.header.logout}
          </button>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {showMapLink ? (
            <Link
              href={withLocalePrefix("/map", locale)}
              className="rounded-full border border-amber-500/40 bg-amber-500/20 px-3 py-1 text-amber-100 transition hover:bg-amber-500/30"
            >
              {t.header.map}
            </Link>
          ) : null}
          {showLeaderboardLink ? (
            <Link
              href={withLocalePrefix("/leaderboard", locale)}
              className="rounded-full border border-amber-500/40 bg-amber-500/20 px-3 py-1 text-amber-100 transition hover:bg-amber-500/30"
            >
              {t.header.leaderboard}
            </Link>
          ) : null}
          <button
            type="button"
            onClick={() => setShowItems(true)}
            className="rounded-full border border-[var(--border)] bg-black/20 px-3 py-1 text-[var(--ink-main)] transition hover:bg-black/30"
          >
            {t.header.items}
          </button>
        </div>
      </div>
      <ItemPanel open={showItems} collectedItems={collectedItems} onClose={() => setShowItems(false)} />
    </header>
  );
}
