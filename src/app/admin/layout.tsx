import type { ReactNode } from "react";
import "@/app/globals.css";

export const metadata = {
  title: "管理端 · 图书馆寻宝游戏",
};

export default function AdminLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="zh-Hans">
      <body className="bg-[#0d0d0c] text-[var(--ink-base)] antialiased">
        {children}
      </body>
    </html>
  );
}
