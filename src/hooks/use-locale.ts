"use client";

import { useParams } from "next/navigation";
import { normalizeLocale, type Locale } from "@/lib/i18n";

type LangParams = {
  lang?: string | string[];
};

export const useLocale = (): Locale => {
  const params = useParams<LangParams>();
  return normalizeLocale(params?.lang);
};
