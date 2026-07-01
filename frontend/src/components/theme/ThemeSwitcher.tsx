"use client"

import { useSyncExternalStore } from "react"
import { Palette } from "lucide-react"
import { THEME_LABELS, THEMES, type Theme } from "./theme"
import {
  getThemeServerSnapshot,
  getThemeSnapshot,
  setTheme,
  subscribeTheme,
} from "./themeStore"

// 色票主題切換器：更新 <html data-theme>、記憶於 localStorage。
// 以 useSyncExternalStore 讀取實際主題（避免 SSR/CSR 不一致與 effect-setState）。
export function ThemeSwitcher({ className = "" }: { className?: string }) {
  const theme = useSyncExternalStore(
    subscribeTheme,
    getThemeSnapshot,
    getThemeServerSnapshot
  )

  return (
    <label
      className={`inline-flex items-center gap-1 cursor-pointer ${className}`}
      aria-label="切換色票主題"
    >
      <Palette className="w-3.5 h-3.5" aria-hidden />
      <select
        value={theme}
        onChange={(e) => setTheme(e.target.value as Theme)}
        className="bg-transparent border-none text-inherit text-xs cursor-pointer focus:outline-none"
        suppressHydrationWarning
      >
        {THEMES.map((t) => (
          <option key={t} value={t} className="text-rs-ink">
            {THEME_LABELS[t]}
          </option>
        ))}
      </select>
    </label>
  )
}
