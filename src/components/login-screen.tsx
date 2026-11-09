"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { CACHE_BUSTER, STUDENT_ID_REGEX } from "@/lib/constants";
import { useLocale } from "@/hooks/use-locale";
import { LOCALES, replaceLocaleInPathname, withLocalePrefix } from "@/lib/i18n";
import { getTranslations } from "@/lib/translations";
import { useGameStore } from "@/store/game-store";

export function LoginScreen() {
  const router = useRouter();
  const locale = useLocale();
  const t = getTranslations(locale);
  const hydrated = useGameStore((state) => state.hasHydrated);
  const player = useGameStore((state) => state.player);
  const role = useGameStore((state) => state.role);
  const login = useGameStore((state) => state.login);

  const [mode, setMode] = useState<"student" | "admin">("student");
  const [studentNo, setStudentNo] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (hydrated && player) {
      router.replace(role === "staff" ? "/admin" : withLocalePrefix("/map", locale));
    }
  }, [hydrated, player, role, router, locale]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const account = studentNo.trim();
    const isAdminLogin = mode === "admin";
    if (!account) {
      setError(t.login.invalidStudentId);
      return;
    }
    // 学生学号规则：2 个字母 + 6 个数字（管理员账号不受此限制）
    if (!isAdminLogin && !STUDENT_ID_REGEX.test(account)) {
      setError(t.login.invalidStudentId);
      return;
    }
    if (isAdminLogin && !password) {
      setError(t.login.passwordRequired);
      return;
    }

    setSubmitting(true);
    setError("");
    try {
      await login(account, isAdminLogin ? password : undefined);
      const nextRole = useGameStore.getState().role;
      router.push(nextRole === "staff" ? "/admin" : withLocalePrefix("/map", locale));
    } catch (err) {
      // 管理员登录失败时后端统一提示「账号或密码错误」
      setError(err instanceof Error ? err.message : t.login.invalidStudentId);
    } finally {
      setSubmitting(false);
    }
  };

  if (!hydrated) {
    return (
      <div className="flex min-h-dvh items-center justify-center px-6 text-center text-[var(--ink-muted)]">
        {t.login.loading}
      </div>
    );
  }

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

      <section className="intro-card w-full max-w-md p-6 md:p-8">
        <div className="flex items-center justify-between">
          <p className="text-xs uppercase tracking-[0.28em] text-[var(--ink-muted)]">{t.brand}</p>
          <div className="flex items-center gap-1 rounded-full border border-[var(--border)] bg-black/15 px-2 py-1">
            <span className="text-xs text-[var(--ink-muted)]">{t.header.language}</span>
            {LOCALES.map((lang) => (
              <Link
                key={lang}
                href={replaceLocaleInPathname("/login", lang)}
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
        <h1 className="treasure-title mt-2 text-3xl md:text-4xl">{t.login.headline}</h1>
        <p className="mt-3 whitespace-pre-line text-sm leading-6 text-[var(--ink-muted)]">
          {mode === "student" ? t.login.description : t.login.adminDescription}
        </p>

        <div className="mt-4 flex gap-1 rounded-full border border-[var(--border)] bg-black/15 p-1 text-xs">
          {(["student", "admin"] as const).map((value) => (
            <button
              key={value}
              type="button"
              onClick={() => {
                setMode(value);
                setError("");
              }}
              className={`flex-1 rounded-full px-3 py-1.5 transition ${
                mode === value
                  ? "bg-amber-500/25 text-amber-100"
                  : "text-[var(--ink-muted)] hover:bg-black/20"
              }`}
            >
              {value === "student" ? t.login.studentRole : t.login.adminRole}
            </button>
          ))}
        </div>

        <form onSubmit={handleSubmit} className="mt-5 space-y-3">
          <label className="block text-sm text-[var(--ink-main)]">
            {t.login.studentIdLabel}
            <input
              value={studentNo}
              onChange={(event) => {
                setStudentNo(event.target.value);
                if (error) setError("");
              }}
              placeholder={t.login.studentIdPlaceholder}
              autoComplete="username"
              className="mt-2 w-full rounded-xl border border-[var(--border)] bg-black/20 px-3 py-2 text-base outline-none placeholder:text-[var(--ink-muted)] focus:border-amber-400/70"
            />
          </label>

          {mode === "admin" ? (
            <label className="block text-sm text-[var(--ink-main)]">
              {t.login.passwordLabel}
              <input
                type="password"
                value={password}
                onChange={(event) => {
                  setPassword(event.target.value);
                  if (error) setError("");
                }}
                placeholder={t.login.passwordPlaceholder}
                autoComplete="current-password"
                className="mt-2 w-full rounded-xl border border-[var(--border)] bg-black/20 px-3 py-2 text-base outline-none placeholder:text-[var(--ink-muted)] focus:border-amber-400/70"
              />
            </label>
          ) : null}

          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-xl border border-amber-500/50 bg-amber-500/25 px-4 py-2 text-base text-amber-100 transition hover:bg-amber-500/35 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting ? t.login.loading : t.login.submit}
          </button>
        </form>

        {error ? <p className="mt-3 text-sm text-amber-100">{error}</p> : null}
      </section>

      <div className="intro-spark intro-spark-a" aria-hidden="true" />
      <div className="intro-spark intro-spark-b" aria-hidden="true" />
      <div className="intro-spark intro-spark-c" aria-hidden="true" />
    </main>
  );
}
