// 意象若水 RUOSHUI 色票主題定義（對應 globals.css 的 [data-theme] 區塊）
export const THEMES = ["blue", "green", "lotus", "pastel", "test05"] as const

export type Theme = (typeof THEMES)[number]

export const DEFAULT_THEME: Theme = "blue"

export const THEME_STORAGE_KEY = "rs-theme"

export const THEME_LABELS: Record<Theme, string> = {
  blue: "海藍",
  green: "森綠",
  lotus: "蓮田",
  pastel: "柔彩",
  test05: "橄欖",
}

export function isTheme(value: unknown): value is Theme {
  return typeof value === "string" && (THEMES as readonly string[]).includes(value)
}
