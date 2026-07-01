import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { ThemeScript } from "@/components/theme/ThemeScript";
import { DEFAULT_THEME } from "@/components/theme/theme";

// 拉丁字型以 next/font self-host（Inter）。繁體中文採系統 CJK 字體堆疊
// （見 globals.css --font-sans），因 Turbopack 對 CJK next/font 產生的
// 大量 @font-face unicode-range 子集無法解析（build 失敗）。
const inter = Inter({
  subsets: ["latin"],
  variable: "--font-inter",
  display: "swap",
});

export const metadata: Metadata = {
  title: "意象若水 RUOSHUI｜生活減法，無負擔的購物體驗",
  description: "意象若水 RUOSHUI 電商平台",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="zh-TW"
      data-theme={DEFAULT_THEME}
      className={`h-full antialiased ${inter.variable}`}
    >
      <body className="min-h-full flex flex-col">
        <ThemeScript />
        {children}
      </body>
    </html>
  );
}
