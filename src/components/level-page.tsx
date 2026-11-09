"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { TOTAL_LEVELS } from "@/lib/constants";
import { useGameStore } from "@/store/game-store";
import { apiFetch } from "@/lib/api";
import type {
  AnswerResult,
  ItemConfig,
  LevelConfig,
  LevelOption,
  QrResult,
} from "@/lib/types";
import { GameHeader } from "@/components/game-header";
import { QrVerifier } from "@/components/qr-verifier";
import { StoryClueBox } from "@/components/story-clue-box";
import { useLocale } from "@/hooks/use-locale";
import { getTranslations } from "@/lib/translations";
import { withLocalePrefix } from "@/lib/i18n";

type LevelPageProps = {
  levelNumber: number;
};

const formatLevel = (level: number) => String(level).padStart(2, "0");

type TabKey = "story" | "question";

const TAB_ICONS: Record<TabKey, string> = {
  story: "📖",
  question: "❓",
};

export function LevelPage({ levelNumber }: LevelPageProps) {
  const router = useRouter();
  const locale = useLocale();
  const t = getTranslations(locale);

  // ---- 服务端状态 ----
  const runId = useGameStore((state) => state.runId);
  const runReady = useGameStore((state) => state.runReady);
  const unlockedLevel = useGameStore((state) => state.unlockedLevel);
  const levelResults = useGameStore((state) => state.levelResults);
  const applySettlement = useGameStore((state) => state.applySettlement);

  const existingResult = levelResults[levelNumber];
  const isCompleted = Boolean(existingResult?.questionPassed && existingResult?.qrPassed);

  // ---- 关卡内容：内容与答案都在服务端，这里只拿「展示字段」----
  const [level, setLevel] = useState<LevelConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    apiFetch<LevelConfig>(
      `/api/levels/${levelNumber}?locale=${encodeURIComponent(locale)}`
    )
      .then((data) => {
        if (cancelled) return;
        setLevel(data);
        setLoadError("");
      })
      .catch((err) => {
        if (cancelled) return;
        setLoadError(err instanceof Error ? err.message : t.level.loadError);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [levelNumber, locale, t.level.loadError]);

  // ---- 本地交互状态 ----
  const [shuffledOptions, setShuffledOptions] = useState<LevelOption[]>([]);
  useEffect(() => {
    if (!level) return;
    const arr = [...level.options];
    for (let i = arr.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [arr[i], arr[j]] = [arr[j], arr[i]];
    }
    setShuffledOptions(arr);
    setSelectedOptions([]);
  }, [level]);

  const [activeTab, setActiveTab] = useState<TabKey>("story");
  const [selectedOptions, setSelectedOptions] = useState<string[]>([]);
  const [attempts, setAttempts] = useState(0);
  const [answerMessage, setAnswerMessage] = useState("");
  const [questionPassedLocal, setQuestionPassedLocal] = useState(false);
  const [qrPassedLocal, setQrPassedLocal] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [showCompletionStory, setShowCompletionStory] = useState(false);
  const [previewItem, setPreviewItem] = useState<ItemConfig | null>(null);

  const questionPassed = Boolean(existingResult?.questionPassed) || questionPassedLocal;
  const qrPassed = Boolean(existingResult?.qrPassed) || qrPassedLocal;
  const shownMessage = isCompleted ? t.level.completedHint : answerMessage;
  const multiple = Boolean(level?.multiple);
  const shownAttempts = existingResult?.attempts ?? attempts;

  useEffect(() => {
    if (!runReady) return;
    if (!existingResult && levelNumber > unlockedLevel) {
      router.replace(withLocalePrefix("/map", locale));
    }
  }, [levelNumber, unlockedLevel, existingResult, runReady, router, locale]);

  const goNext = () => {
    if (levelNumber >= TOTAL_LEVELS) {
      router.push(withLocalePrefix("/result", locale));
      return;
    }
    router.push(withLocalePrefix(`/levels/${formatLevel(levelNumber + 1)}`, locale));
  };

  const checkAnswer = async () => {
    if (isCompleted || submitting) {
      return;
    }
    if (!runId) {
      setAnswerMessage(t.level.runNotReady);
      return;
    }
    if (selectedOptions.length === 0) {
      setAnswerMessage(t.level.selectFirst);
      return;
    }

    setSubmitting(true);
    try {
      // 判题在服务端：客户端只提交「我选了什么」，拿回「对/错 + 结算」
      const result = await apiFetch<AnswerResult>(
        `/api/runs/${runId}/levels/${levelNumber}/answer`,
        { method: "POST", body: { optionIds: selectedOptions } }
      );

      setAttempts(result.attempts);
      if (result.questionPassed) {
        setQuestionPassedLocal(true);
        setAnswerMessage(t.level.answerCorrect(result.score));
        setActiveTab("story");
        if (result.settlement) {
          applySettlement(levelNumber, result.settlement);
        }
      } else {
        // 答错不提示，直接让玩家再试；错选不倒扣分数
        setAnswerMessage("");
      }
    } catch (err) {
      setAnswerMessage(err instanceof Error ? err.message : t.level.submitError);
    } finally {
      setSubmitting(false);
    }
  };

  const handleQrVerify = useCallback(
    async (value: string): Promise<boolean> => {
      if (!runId) {
        throw new Error(t.level.runNotReady);
      }
      const result = await apiFetch<QrResult>(
        `/api/runs/${runId}/levels/${levelNumber}/qr`,
        { method: "POST", body: { answer: value } }
      );
      if (result.correct) {
        setQrPassedLocal(true);
        if (result.settlement) {
          applySettlement(levelNumber, result.settlement);
        }
      }
      return result.correct;
    },
    [runId, levelNumber, applySettlement, t.level.runNotReady]
  );

  if (loading) {
    return (
      <div className="pb-8">
        <GameHeader title={t.level.title(levelNumber)} />
        <p className="mx-auto mt-8 w-[min(980px,92vw)] text-center text-sm text-[var(--ink-muted)]">
          {t.level.loading}
        </p>
      </div>
    );
  }

  if (!level || loadError) {
    return (
      <div className="pb-8">
        <GameHeader title={t.level.title(levelNumber)} />
        <div className="treasure-panel mx-auto mt-5 flex w-[min(980px,92vw)] flex-col items-center gap-3 p-6 text-center">
          <p className="text-sm text-red-300">{loadError || t.level.loadError}</p>
          <Link
            href={withLocalePrefix("/map", locale)}
            className="rounded-xl border px-4 py-2 text-sm transition hover:bg-black/30"
            style={{ borderColor: "var(--border)" }}
          >
            {t.level.backToMap}
          </Link>
        </div>
      </div>
    );
  }

  const items = level.items;
  const storyImageUrl = level.storyImageUrl;
  const storyImageAlt = level.storyImageAlt ?? "";
  const storyImageCaption = level.storyImageCaption;

  return (
    <div className="pb-8">
      <GameHeader title={t.level.title(levelNumber)} subtitle={level.title} />

      <section className="mx-auto mt-4 w-[min(980px,92vw)]">
        <QrVerifier
          levelNumber={level.level}
          location={level.qrLocation}
          locationNote={level.qrLocationNote}
          initiallyVerified={qrPassed}
          onVerify={handleQrVerify}
        />
      </section>

      <main className="mx-auto mt-5 grid w-[min(980px,92vw)] gap-4 md:grid-cols-5">
        {/* 主內容區：標籤切換 */}
        <section className="treasure-panel flex flex-col p-0 md:col-span-3">
          {/* 標籤欄 */}
          <div className="flex border-b border-[var(--border)]">
            {(["story", "question"] as TabKey[]).map((tab) => (
              <button
                key={tab}
                type="button"
                onClick={() => setActiveTab(tab)}
                className={`flex-1 px-4 py-3 text-sm font-medium transition ${
                  activeTab === tab
                    ? "border-b-2 border-amber-400 text-amber-100"
                    : "text-[var(--ink-muted)] hover:text-[var(--ink-main)]"
                }`}
              >
                <span className="mr-1.5">{TAB_ICONS[tab]}</span>
                <span className="relative">
                  {tab === "story" ? t.level.storyHeading : t.level.questionHeading}
                  {tab === "question" && qrPassed && !questionPassed && (
                    <span className="absolute -right-2.5 -top-0.5 flex h-2 w-2">
                      <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-amber-400 opacity-75" />
                      <span className="relative inline-flex h-2 w-2 rounded-full bg-amber-400" />
                    </span>
                  )}
                </span>
              </button>
            ))}
          </div>

          {/* 標籤內容 */}
          <div className="p-4">
            {activeTab === "story" ? (
              <>
                <h2 className="treasure-title text-xl">{level.title}</h2>
                <p className="mt-3 leading-7" style={{ color: "var(--ink-main)", opacity: 0.95 }}>
                  {level.story}
                </p>
                <StoryClueBox
                  videoUrl={level.videoUrl}
                  imageUrl={storyImageUrl}
                  imageAlt={storyImageAlt}
                  imageCaption={storyImageCaption}
                />
              </>
            ) : (
              <>
                <div className="flex items-center justify-between gap-2">
                  <h2 className="treasure-title text-lg">{t.level.questionHeading}</h2>
                  {multiple ? (
                    <span className="rounded-full border border-amber-500/40 bg-amber-500/20 px-2 py-0.5 text-xs text-amber-100">
                      {t.level.multiSelectHint}
                    </span>
                  ) : null}
                </div>
                <p className="mt-2 text-base leading-7" style={{ color: "var(--ink-main)" }}>
                  {level.question}
                </p>

                <div className="mt-4 space-y-2">
                  {shuffledOptions.map((option) => (
                    <label
                      key={option.id}
                      className="flex cursor-pointer items-center gap-2.5 rounded-lg border bg-black/15 px-3.5 py-2.5 text-sm transition hover:bg-black/25"
                      style={{ borderColor: "var(--border)" }}
                    >
                      <input
                        type={multiple ? "checkbox" : "radio"}
                        name={`level-question-${level.level}`}
                        value={option.id}
                        checked={selectedOptions.includes(option.id)}
                        onChange={(event) => {
                          const { checked, value } = event.target;
                          setSelectedOptions((prev) => {
                            if (!multiple) {
                              return [value];
                            }
                            if (checked) {
                              return Array.from(new Set([...prev, value]));
                            }
                            return prev.filter((item) => item !== value);
                          });
                        }}
                      />
                      {option.label}
                    </label>
                  ))}
                </div>

                <button
                  type="button"
                  onClick={() => void checkAnswer()}
                  disabled={questionPassed || submitting}
                  className="mt-4 w-full rounded-xl bg-amber-400 py-2.5 text-sm font-semibold text-amber-950 shadow-sm shadow-amber-400/20 transition hover:bg-amber-300 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-40"
                >
                  {t.level.submitAnswer}
                </button>

                {shownMessage ? <p className="mt-3 text-sm text-amber-100">{shownMessage}</p> : null}
              </>
            )}
          </div>
        </section>

        {/* 側邊欄：結算 */}
        <section className="treasure-panel flex flex-col gap-3 p-4 md:col-span-2">
          <h3 className="treasure-title text-lg">{t.level.settlementHeading}</h3>

          {/* 步驟進度 */}
          <div className="flex items-center gap-1.5">
            {[
              { label: t.level.qrStatus, done: qrPassed, active: !qrPassed },
              { label: t.level.questionStatus, done: questionPassed, active: qrPassed && !questionPassed },
              { label: t.level.completeAndNext, done: false, active: questionPassed && qrPassed },
            ].map((step, i) => (
              <div key={i} className="flex flex-1 items-center gap-1.5">
                <div className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-bold transition-all duration-500 ${
                  step.done
                    ? "scale-100 bg-green-500/20 text-green-400"
                    : step.active
                    ? "scale-110 bg-amber-400 text-amber-950 shadow-sm shadow-amber-400/30"
                    : "scale-100 bg-white/5 text-[var(--ink-muted)]"
                }`}>
                  {step.done ? (
                    <svg className="h-3.5 w-3.5" fill="currentColor" viewBox="0 0 20 20"><path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" /></svg>
                  ) : (
                    i + 1
                  )}
                </div>
                <span className={`text-[11px] font-medium leading-tight ${
                  step.active ? "text-amber-300" : step.done ? "text-green-400" : "text-[var(--ink-muted)]"
                }`}>
                  {step.label}
                </span>
                {i < 2 && <div className={`h-px flex-1 ${qrPassed && i === 0 ? "bg-green-500/30" : "bg-white/10"}`} />}
              </div>
            ))}
          </div>

          {shownMessage ? (
            <p className="text-sm text-amber-100">{shownMessage}</p>
          ) : null}

          {questionPassed && level.completionStory ? (
            <button
              type="button"
              onClick={() => setShowCompletionStory(!showCompletionStory)}
              className="w-full rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 text-left transition hover:bg-amber-500/15"
            >
              <div className="flex items-center justify-between">
                <span className="text-xs uppercase tracking-[0.2em] text-amber-300">
                  {t.level.completionStoryHeading}
                </span>
                <svg
                  className={`h-4 w-4 shrink-0 text-amber-400 transition-transform ${showCompletionStory ? "rotate-180" : ""}`}
                  fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
                </svg>
              </div>
              <p
                className={`mt-2 text-base leading-7 ${showCompletionStory ? "" : "line-clamp-2"}`}
                style={{ color: "var(--ink-main)", opacity: 0.95 }}
              >
                {level.completionStory}
              </p>

              {showCompletionStory && items.length > 0 && (
                <div className="mt-3 flex flex-wrap gap-2" onClick={(e) => e.stopPropagation()}>
                  {items.map((it) => (
                    <div
                      key={it.id}
                      className="flex items-center gap-2 rounded-lg border bg-black/25 px-2.5 py-2 transition hover:bg-black/35"
                      style={{ borderColor: "var(--border)" }}
                    >
                      <button
                        type="button"
                        onClick={() => setPreviewItem(it)}
                        className="h-10 w-10 shrink-0 overflow-hidden rounded-md border bg-black/30 transition hover:opacity-80"
                        style={{ borderColor: "var(--border)" }}
                        aria-label={`查看道具大图：${it.name}`}
                      >
                        <img src={it.imageUrl} alt={it.name} className="h-full w-full object-cover" />
                      </button>
                      <div>
                        <p className="text-xs font-medium text-[var(--ink-main)]">{it.name}</p>
                        <p className="text-[11px] text-[var(--ink-muted)]">{t.level.itemUnlockHint}</p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </button>
          ) : null}

          <button
            type="button"
            onClick={goNext}
            disabled={!questionPassed || !qrPassed || submitting}
            className="rounded-xl bg-amber-400 px-4 py-3 text-base font-semibold text-amber-950 shadow-md shadow-amber-400/25 transition hover:bg-amber-300 hover:shadow-lg hover:shadow-amber-400/35 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none"
          >
            {t.level.completeAndNext}
          </button>

          {shownAttempts > 0 ? (
            <p className="text-center text-xs text-[var(--ink-muted)]">
              {t.level.attemptsLabel(shownAttempts)}
            </p>
          ) : null}

          <div className="flex gap-2">
            <Link
              href={withLocalePrefix("/map", locale)}
              className="rounded-xl border bg-black/25 px-3 py-2 text-sm transition hover:bg-black/35"
              style={{ borderColor: "var(--border)" }}
            >
              {t.level.backToMap}
            </Link>
          </div>
        </section>
      </main>

      {previewItem ? (
        <button
          type="button"
          onClick={() => setPreviewItem(null)}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4"
          aria-label="关闭道具大图预览"
        >
          <div className="max-h-[90vh] w-full max-w-5xl overflow-hidden rounded-2xl bg-black/90 shadow-2xl">
            <img
              src={previewItem.imageUrl}
              alt={previewItem.name}
              className="max-h-[90vh] w-full object-contain"
            />
            <div className="border-t border-white/10 px-4 py-3">
              <p className="text-base font-semibold text-white">{previewItem.name}</p>
              <p className="mt-1 text-sm text-white/70">{previewItem.description}</p>
            </div>
          </div>
        </button>
      ) : null}
    </div>
  );
}
