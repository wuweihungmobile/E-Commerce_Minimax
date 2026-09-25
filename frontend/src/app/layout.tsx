import type { Metadata } from "next";
import { headers } from "next/headers";
import localFont from "next/font/local";
import "./globals.css";
import { ThemeScript } from "@/components/theme/ThemeScript";
import { DEFAULT_THEME } from "@/components/theme/theme";

// 拉丁字型以 next/font/local self-host（Inter latin variable woff2，committed 於 ./fonts）。
// 改用 local（AI-2303）以消除 build 期對 Google Fonts 的網路依賴（原 next/font/google 於
// build 時抓 Inter，網路抖動即 build 失敗）。繁體中文仍採系統 CJK 字體堆疊
// （見 globals.css --font-sans），因 Turbopack 對 CJK next/font 大量 unicode-range 子集無法解析。
const inter = localFont({
  src: "./fonts/Inter-latin.woff2",
  variable: "--font-inter",
  display: "swap",
  weight: "400 700",
});

export const metadata: Metadata = {
  title: "意象若水 RUOSHUI｜生活減法，無負擔的購物體驗",
  description: "意象若水 RUOSHUI 電商平台",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  // nonce 由 src/proxy.ts 每次請求產生（嚴格 CSP）。讀取請求標頭會讓所有頁面改為動態渲染——
  // 這是 nonce 型 CSP 的必要條件：靜態頁在 build 時沒有請求，無 nonce 可套用，script 會被 CSP 擋下。
  const nonce = (await headers()).get("x-nonce") ?? undefined;

  return (
    <html
      lang="zh-TW"
      // data-theme 於 hydration 前由 ThemeScript 依 localStorage 改寫，故此處 suppressHydrationWarning
      suppressHydrationWarning
      data-theme={DEFAULT_THEME}
      className={`h-full antialiased ${inter.variable}`}
    >
      <body className="min-h-full flex flex-col">
        <ThemeScript nonce={nonce} />
        {children}
      </body>
    </html>
  );
}
