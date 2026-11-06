"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useGameStore } from "@/store/game-store";
import { useLocale } from "@/hooks/use-locale";
import { getTranslations } from "@/lib/translations";
import { withLocalePrefix } from "@/lib/i18n";

type AuthGateProps = {
  children: React.ReactNode;
};

export function AuthGate({ children }: AuthGateProps) {
  const router = useRouter();
  const locale = useLocale();
  const t = getTranslations(locale);
  const hydrated = useGameStore((state) => state.hasHydrated);
  const player = useGameStore((state) => state.player);
  const refreshRun = useGameStore((state) => state.refreshRun);

  useEffect(() => {
    if (hydrated && !player) {
      router.replace(withLocalePrefix("/login", locale));
      return;
    }
    if (hydrated && player) {
      // 进度以服务端为准：刷新页面 / 换设备后自动恢复
      void refreshRun();
    }
  }, [hydrated, player, refreshRun, router, locale]);

  if (!hydrated) {
    return (
      <div className="flex min-h-dvh items-center justify-center px-6 text-center text-[var(--ink-muted)]">
        {t.authGate.loading}
      </div>
    );
  }

  if (!player) {
    return null;
  }

  return <>{children}</>;
}
