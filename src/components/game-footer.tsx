"use client";

import { useLocale } from "@/hooks/use-locale";
import { getTranslations } from "@/lib/translations";

export function GameFooter() {
  const locale = useLocale();
  const t = getTranslations(locale);

  return (
    <footer className="mt-auto border-t border-[var(--border)] py-4 text-center">
      <p className="text-xs text-[var(--ink-muted)]">{t.footer.producer}</p>
      <p className="mt-1 text-[0.65rem] text-[var(--ink-muted)] opacity-60">
        {t.footer.disclaimer}
      </p>
    </footer>
  );
}
