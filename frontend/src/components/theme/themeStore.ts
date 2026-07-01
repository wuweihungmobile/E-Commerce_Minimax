// 主題外部狀態存取（供 useSyncExternalStore 使用，避免 effect 內同步 setState）。
// 僅限 client 元件匯入（使用 window / document）。
import { DEFAULT_THEME, isTheme, THEME_STORAGE_KEY, type Theme } from "./theme"

let listeners: Array<() => void> = []

export function subscribeTheme(callback: () => void) {
  if (typeof window !== "undefined") {
    window.addEventListener("storage", callback)
  }
  listeners.push(callback)
  return () => {
    listeners = listeners.filter((l) => l !== callback)
    if (typeof window !== "undefined") {
      window.removeEventListener("storage", callback)
    }
  }
}

export function getThemeSnapshot(): Theme {
  const t = document.documentElement.getAttribute("data-theme")
  return isTheme(t) ? t : DEFAULT_THEME
}

export function getThemeServerSnapshot(): Theme {
  return DEFAULT_THEME
}

export function setTheme(next: Theme) {
  document.documentElement.setAttribute("data-theme", next)
  try {
    localStorage.setItem(THEME_STORAGE_KEY, next)
  } catch {
    // localStorage 不可用時忽略（例如隱私模式）
  }
  listeners.forEach((l) => l())
}
