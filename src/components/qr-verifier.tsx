"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLocale } from "@/hooks/use-locale";
import { getTranslations } from "@/lib/translations";

type QrVerifierProps = {
  /** 交给服务端校验；返回是否通过 */
  onVerify: (value: string) => Promise<boolean>;
  levelNumber: number;
  location?: string;
  locationNote?: string;
  /** 已经验证过（从服务端进度恢复） */
  initiallyVerified?: boolean;
};

type Html5QrcodeInstance = {
  start: (
    cameraConfig: { facingMode: string },
    config: { fps: number; qrbox: { width: number; height: number } },
    onSuccess: (decodedText: string) => void,
    onError: (error: string) => void
  ) => Promise<unknown>;
  stop: () => Promise<unknown>;
  clear: () => void;
};

export function QrVerifier({
  onVerify,
  levelNumber,
  location,
  locationNote,
  initiallyVerified = false,
}: QrVerifierProps) {
  const locale = useLocale();
  const t = getTranslations(locale);
  const containerId = useMemo(() => `qr-scanner-level-${levelNumber}`, [levelNumber]);
  const scannerRef = useRef<Html5QrcodeInstance | null>(null);

  const [expanded, setExpanded] = useState(false);
  const [verified, setVerified] = useState(initiallyVerified);
  const [cameraActive, setCameraActive] = useState(false);
  const [manualInput, setManualInput] = useState("");
  const [feedback, setFeedback] = useState("");
  const [verifying, setVerifying] = useState(false);

  useEffect(() => {
    if (initiallyVerified) setVerified(true);
  }, [initiallyVerified]);

  const stopCamera = useCallback(async () => {
    if (!scannerRef.current) {
      return;
    }

    try {
      await scannerRef.current.stop();
    } catch {
      // 攝影鏡頭可能尚未成功啟動，忽略停止異常。
    }

    scannerRef.current.clear();

    scannerRef.current = null;
    setCameraActive(false);
  }, []);

  const verifyValue = useCallback(
    async (value: string) => {
      const normalized = value.trim();
      if (!normalized) {
        setFeedback(t.qr.emptyValue);
        return;
      }
      if (verifying) {
        return;
      }

      setVerifying(true);
      try {
        const passed = await onVerify(normalized);
        if (passed) {
          setFeedback(t.qr.success);
          setVerified(true);
          setExpanded(false);
        } else {
          setFeedback(t.qr.mismatch);
        }
      } catch (err) {
        setFeedback(err instanceof Error ? err.message : t.qr.mismatch);
      } finally {
        setVerifying(false);
      }
    },
    [onVerify, t, verifying]
  );

  const startCamera = useCallback(async () => {
    setFeedback("");

    try {
      const { Html5Qrcode } = await import("html5-qrcode");
      const scanner = new Html5Qrcode(containerId) as unknown as Html5QrcodeInstance;
      scannerRef.current = scanner;

      await scanner.start(
        { facingMode: "environment" },
        { fps: 10, qrbox: { width: 220, height: 220 } },
        (decodedText) => {
          setManualInput(decodedText);
          void verifyValue(decodedText);
          void stopCamera();
        },
        () => {
          // 連續掃描時會頻繁拋錯，忽略單次解析失敗。
        }
      );

      setCameraActive(true);
    } catch {
      setFeedback(t.qr.cameraError);
      void stopCamera();
    }
  }, [containerId, stopCamera, verifyValue, t]);

  useEffect(() => {
    return () => {
      void stopCamera();
    };
  }, [stopCamera]);

  return (
    <section className="treasure-panel overflow-hidden p-0">
      {/* 摺疊狀態 */}
      <button
        type="button"
        onClick={() => setExpanded(!expanded)}
        className={`group flex w-full items-center gap-3 px-5 py-4 text-left transition ${
          verified
            ? ""
            : "bg-amber-500/10 hover:bg-amber-500/15"
        }`}
      >
        <span className={`relative flex h-10 w-10 shrink-0 items-center justify-center rounded-xl transition ${
          verified ? "bg-green-500/15" : "bg-amber-500/20 group-hover:bg-amber-500/25"
        }`}>
          {!verified && !expanded && (
            <span className="absolute -right-1 -top-1 h-3 w-3 animate-pulse rounded-full bg-amber-400" />
          )}
          {verified ? (
            <svg className="h-5 w-5 text-green-400" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
            </svg>
          ) : (
            <svg className="h-5 w-5 text-amber-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
              <path strokeLinecap="round" d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
              <circle cx="12" cy="13" r="3" strokeLinecap="round" />
            </svg>
          )}
        </span>
        <div className="min-w-0 flex-1">
          <p className={`text-sm font-semibold ${verified ? "text-green-400" : "text-amber-300"}`}>
            {verified ? t.qr.success : t.qr.heading}
          </p>
          <p className="mt-0.5 truncate text-sm font-medium text-[var(--ink-main)]">
            {location ?? `${t.qr.locationLabel} ${levelNumber}`}
          </p>
        </div>
        {verified ? (
          <svg className="h-5 w-5 shrink-0 text-[var(--ink-muted)]" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
            <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
          </svg>
        ) : (
          <span className="shrink-0 rounded-lg bg-amber-500/15 px-3 py-1.5 text-xs font-semibold text-amber-300 transition group-hover:bg-amber-500/25">
            {t.qr.startScan}
          </span>
        )}
      </button>

      {/* 展開內容 */}
      {expanded && (
        <>
          {/* 標題區 */}
          <div className="space-y-1 border-b border-[var(--border)] px-5 py-4">
            <h3 className="text-sm font-medium tracking-wide text-amber-300">{t.qr.heading}</h3>
            <p className="text-lg font-semibold text-[var(--ink-main)]">
              {location ?? `${t.qr.locationLabel} ${levelNumber}`}
            </p>
            {locationNote ? (
              <p className="text-xs text-[var(--ink-muted)]">{locationNote}</p>
            ) : null}
          </div>

          {/* 掃碼區域 */}
          <div className="relative mx-5 mt-4 overflow-hidden rounded-2xl bg-black/40">
            <div id={containerId} className="w-full [&_video]:!w-full [&_video]:!h-auto [&_video]:block" />

            {cameraActive && (
              <div className="pointer-events-none absolute inset-3 z-10 rounded-2xl border-2 border-amber-400/50 shadow-[0_0_30px_rgba(251,191,36,0.15)]">
                <span className="absolute -left-[2px] -top-[2px] h-5 w-5 rounded-br-lg border-l-2 border-t-2 border-amber-400/70" />
                <span className="absolute -right-[2px] -top-[2px] h-5 w-5 rounded-bl-lg border-r-2 border-t-2 border-amber-400/70" />
                <span className="absolute -bottom-[2px] -left-[2px] h-5 w-5 rounded-tr-lg border-b-2 border-l-2 border-amber-400/70" />
                <span className="absolute -bottom-[2px] -right-[2px] h-5 w-5 rounded-tl-lg border-b-2 border-r-2 border-amber-400/70" />
              </div>
            )}

            {!cameraActive && (
              <div className="flex h-[280px] flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed border-white/15">
                <svg className="h-10 w-10 text-white/25" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="1.5">
                  <path strokeLinecap="round" d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
                  <circle cx="12" cy="13" r="3" strokeLinecap="round" />
                </svg>
                <p className="text-xs text-white/35">{t.qr.scanArea}</p>
              </div>
            )}
          </div>

          {/* 掃碼按鈕 */}
          <div className="mx-5 mt-3">
            {!cameraActive ? (
              <button
                type="button"
                onClick={startCamera}
                className="flex w-full items-center justify-center gap-2 rounded-xl border border-amber-500/40 bg-amber-500/15 py-2.5 text-sm font-medium text-amber-100 transition hover:bg-amber-500/25 active:scale-[0.98]"
              >
                <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="2">
                  <path strokeLinecap="round" d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
                  <circle cx="12" cy="13" r="3" strokeLinecap="round" />
                </svg>
                {t.qr.startScan}
              </button>
            ) : (
              <button
                type="button"
                onClick={() => void stopCamera()}
                className="flex w-full items-center justify-center gap-2 rounded-xl border border-red-500/40 bg-red-500/10 py-2.5 text-sm font-medium text-red-300 transition hover:bg-red-500/20 active:scale-[0.98]"
              >
                <svg className="h-4 w-4" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8 7a1 1 0 00-1 1v4a1 1 0 001 1h4a1 1 0 001-1V8a1 1 0 00-1-1H8z" clipRule="evenodd" />
                </svg>
                {t.qr.stopScan}
              </button>
            )}
          </div>

          {/* 手動輸入區 */}
          <div className="mx-5 mb-4 mt-4 border-t border-[var(--border)] pt-3">
            <p className="mb-2 text-xs text-[var(--ink-muted)]">{t.qr.manualPlaceholder}</p>
            <div className="flex gap-2">
              <input
                value={manualInput}
                onChange={(event) => setManualInput(event.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") void verifyValue(manualInput);
                }}
                placeholder={t.qr.manualPlaceholder}
                className="flex-1 rounded-xl border border-[var(--border)] bg-black/20 px-3.5 py-2.5 text-sm text-[var(--ink-main)] outline-none ring-0 placeholder:text-[var(--ink-muted)] focus:border-amber-400/60"
              />
              <button
                type="button"
                onClick={() => void verifyValue(manualInput)}
                disabled={verifying}
                className="shrink-0 rounded-xl bg-amber-500/20 px-5 py-2.5 text-sm font-medium text-amber-100 transition hover:bg-amber-500/30 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50"
              >
                {t.qr.manualVerify}
              </button>
            </div>
          </div>

          {/* 反饋訊息 */}
          {feedback ? (
            <div className={`mx-5 mb-4 rounded-lg border px-3.5 py-2.5 text-sm font-medium ${
              feedback === t.qr.success
                ? "border-green-500/30 bg-green-500/10 text-green-400"
                : feedback === t.qr.mismatch
                ? "border-red-500/30 bg-red-500/10 text-red-400"
                : "border-amber-500/30 bg-amber-500/10 text-amber-100"
            }`}>
              {feedback}
            </div>
          ) : null}
        </>
      )}
    </section>
  );
}
