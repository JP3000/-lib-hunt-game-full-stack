export const LOCALES = ["zh-Hant", "zh-Hans", "en"] as const;

export type Locale = (typeof LOCALES)[number];

export const DEFAULT_LOCALE: Locale = "zh-Hant";

export const isLocale = (value: string): value is Locale =>
  LOCALES.includes(value as Locale);

export const normalizeLocale = (value: string | string[] | undefined): Locale => {
  if (typeof value === "string" && isLocale(value)) {
    return value;
  }

  return DEFAULT_LOCALE;
};

export const withLocalePrefix = (path: string, locale: Locale) => {
  const normalized = path.startsWith("/") ? path : `/${path}`;
  return `/${locale}${normalized}`;
};

export const replaceLocaleInPathname = (pathname: string, locale: Locale) => {
  const normalized = pathname.startsWith("/") ? pathname : `/${pathname}`;
  const segments = normalized.split("/");

  if (segments.length > 1 && isLocale(segments[1])) {
    segments[1] = locale;
    return segments.join("/") || "/";
  }

  return withLocalePrefix(normalized, locale);
};
