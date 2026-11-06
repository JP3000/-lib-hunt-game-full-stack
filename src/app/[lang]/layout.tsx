import type { Metadata } from "next";
import { Cinzel, Noto_Sans_SC } from "next/font/google";
import "../globals.css";
import { DEFAULT_LOCALE, LOCALES, type Locale, isLocale } from "@/lib/i18n";
import { getTranslations } from "@/lib/translations";
import { notFound } from "next/navigation";
import { GameFooter } from "@/components/game-footer";

const cinzel = Cinzel({
  variable: "--font-display",
  subsets: ["latin"],
  weight: ["600", "700"],
});

const notoSansSc = Noto_Sans_SC({
  variable: "--font-body",
  subsets: ["latin"],
  weight: ["400", "500", "700"],
});

type LangParams = {
  lang: string;
};

export async function generateStaticParams() {
  return LOCALES.map((lang) => ({ lang }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<LangParams>;
}): Promise<Metadata> {
  const { lang } = await params;
  const locale: Locale = isLocale(lang) ? lang : DEFAULT_LOCALE;
  const t = getTranslations(locale);

  return {
    title: t.gameName,
    description: t.metaDescription,
    icons: {
      icon: "/favicon.png",
    },
  };
}

export default async function RootLayout({
  children,
  params,
}: Readonly<{
  children: React.ReactNode;
  params: Promise<LangParams>;
}>) {
  const { lang } = await params;
  if (!isLocale(lang)) {
    notFound();
  }

  const locale: Locale = lang;

  return (
    <html
      lang={locale}
      className={`${cinzel.variable} ${notoSansSc.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <body className="min-h-full flex flex-col">{children}<GameFooter /></body>
    </html>
  );
}
