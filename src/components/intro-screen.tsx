"use client";

import Link from "next/link";
import { CACHE_BUSTER } from "@/lib/constants";
import { useLocale } from "@/hooks/use-locale";
import { LOCALES, replaceLocaleInPathname, withLocalePrefix } from "@/lib/i18n";
import { getTranslations } from "@/lib/translations";

export function IntroScreen() {
  const locale = useLocale();
  const t = getTranslations(locale);

  return (
    <main className="intro-shell">
      <video
        className="intro-video"
        autoPlay
        muted
        loop
        playsInline
        preload="metadata"
        aria-hidden="true"
      >
        <source src={`/assets/intro.mp4?v=${CACHE_BUSTER}`} type="video/mp4" />
      </video>
      <div className="intro-video-overlay" aria-hidden="true" />
      <div className="intro-glow" aria-hidden="true" />
      <div className="intro-orbit" aria-hidden="true" />

      <section className="intro-card">
        <div className="flex items-center justify-between">
          <p className="text-xs uppercase tracking-[0.28em] text-[var(--ink-muted)]">{t.brand}</p>
          <div className="flex items-center gap-1 rounded-full border border-[var(--border)] bg-black/15 px-2 py-1">
            <span className="text-xs text-[var(--ink-muted)]">{t.header.language}</span>
            {LOCALES.map((lang) => (
              <Link
                key={lang}
                href={replaceLocaleInPathname("/", lang)}
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
        </div>
        <p className="intro-eyebrow">{t.intro.eyebrow}</p>
        <h1 className="intro-title">{t.intro.headline}</h1>
        <p className="intro-subtitle">{t.intro.subtitle}</p>
        <div className="intro-actions">
          <Link className="intro-cta" href={withLocalePrefix("/login", locale)}>
            {t.intro.start}
          </Link>
        </div>
      </section>

      <div className="intro-spark intro-spark-a" aria-hidden="true" />
      <div className="intro-spark intro-spark-b" aria-hidden="true" />
      <div className="intro-spark intro-spark-c" aria-hidden="true" />
    </main>
  );
}
