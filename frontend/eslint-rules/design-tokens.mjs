// 設計稿 token 守門（Sprint 328）：禁止在字串裡寫 Tailwind 預設色盤 class、陰影 class、任意 hex 色值。
//
// 為什麼：全站 UI 以 docs/01_requirements/Design_UI/Homepage (standalone) _OK.html 為 BASE。
// 顏色一律走 --rs-* token（globals.css）才會跟著 <html data-theme> 換主題；設計稿明文「嚴禁陰影，僅用 1px 實線框與色差分層」、
// 「除 #FFFFFF 外，元件中嚴禁手寫 Hex」。這條規則把這些約定變成 lint 錯誤（pre-commit／CI／pre-push 都會跑 lint），
// 避免日後又長出一批 text-gray-500、shadow-sm。
//
// 檢查對象：所有字串字面值與 template literal 的靜態片段（className、cn()／clsx() 的參數、狀態表裡的 class 字串都涵蓋）。
// 允許：bg-white／text-white／bg-black/50（設計稿允許 #FFFFFF；遮罩是純黑透明）、shadow-none。
// 對照表與寫法見 docs/07_design/UI_DESIGN_SYSTEM_CONVENTIONS.md。

const COLORS =
  "gray|slate|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose"
const UTILS =
  "bg|text|border-[trblxy]|border|ring-offset|ring|from|to|via|divide|fill|stroke|outline|decoration|placeholder|accent|caret"
const VARIANTS = "(?:[a-z0-9\\[\\]_-]+:)*"
const BOUNDARY_BEFORE = "(?<![A-Za-z0-9_-])"
const BOUNDARY_AFTER = "(?![A-Za-z0-9_-])"

const PALETTE = new RegExp(
  `${BOUNDARY_BEFORE}${VARIANTS}(?:${UTILS})-(?:${COLORS})-\\d{2,3}(?:/\\d{1,3})?${BOUNDARY_AFTER}`,
  "g"
)
const SHADOW = new RegExp(
  `${BOUNDARY_BEFORE}${VARIANTS}shadow(?:-(?:2xs|xs|sm|md|lg|xl|2xl|inner))?${BOUNDARY_AFTER}`,
  "g"
)
const HEX = new RegExp(
  `${BOUNDARY_BEFORE}${VARIANTS}(?:${UTILS}|shadow)-\\[#[0-9a-fA-F]{3,8}\\]`,
  "g"
)

/** 依 util 與色系給出該改成什麼（只是提示，對照表以文件為準）。 */
function hint(token) {
  const m = token.match(new RegExp(`(${UTILS})-(${COLORS})-(\\d{2,3})`))
  if (!m) return "rs-* token"
  const [, util, color, shade] = m
  const n = Number(shade)
  const base = util.startsWith("border-") ? "border" : util === "ring-offset" ? "ring" : util
  if (["gray", "slate", "zinc", "neutral", "stone"].includes(color)) {
    if (base === "text") return n >= 700 ? "text-rs-ink" : n >= 500 ? "text-rs-ink-muted" : "text-rs-disabled-text"
    if (base === "bg") return n <= 50 ? "bg-rs-bg-base" : n <= 100 ? "bg-rs-disabled-bg" : "bg-rs-hairline"
    return `${base}-rs-hairline`
  }
  if (color === "blue") return base === "bg" ? "bg-rs-primary／bg-rs-tertiary" : `${base}-rs-primary`
  if (color === "red" || color === "rose") return base === "bg" ? "bg-rs-error／bg-rs-error/10" : `${base}-rs-error`
  if (["green", "emerald", "lime"].includes(color)) return base === "bg" ? "bg-rs-success／bg-rs-success/10" : `${base}-rs-success`
  if (["yellow", "amber"].includes(color)) return base === "text" ? "text-rs-ink" : `${base}-rs-warning`
  if (["orange", "pink", "fuchsia"].includes(color)) return `${base}-rs-accent`
  return `${base}-rs-info`
}

const rule = {
  meta: {
    type: "problem",
    docs: { description: "禁止 Tailwind 預設色盤、陰影與寫死 hex；顏色與層次一律走設計稿 token。" },
    schema: [],
    messages: {
      palette:
        "禁止使用 Tailwind 預設色盤 class「{{token}}」：請改用設計稿 token（例如 {{hint}}）。對照表見 docs/07_design/UI_DESIGN_SYSTEM_CONVENTIONS.md。",
      shadow:
        "禁止使用陰影 class「{{token}}」：設計稿規定「嚴禁陰影，僅用 1px 實線框與色差分層」，請改用 border border-rs-hairline。",
      hex: "禁止在 class 裡寫死色碼「{{token}}」：設計稿鐵律「除 #FFFFFF 外，元件中嚴禁手寫 Hex」，請改用 rs-* token。",
    },
  },
  create(context) {
    function check(node, text) {
      if (!text || text.length < 4) return
      for (const m of text.matchAll(PALETTE)) {
        context.report({ node, messageId: "palette", data: { token: m[0], hint: hint(m[0]) } })
      }
      for (const m of text.matchAll(SHADOW)) {
        context.report({ node, messageId: "shadow", data: { token: m[0] } })
      }
      for (const m of text.matchAll(HEX)) {
        context.report({ node, messageId: "hex", data: { token: m[0] } })
      }
    }
    return {
      Literal(node) {
        if (typeof node.value === "string") check(node, node.value)
      },
      TemplateElement(node) {
        check(node, node.value.raw)
      },
    }
  },
}

const designTokensPlugin = { rules: { "no-raw-palette": rule } }

export default designTokensPlugin
