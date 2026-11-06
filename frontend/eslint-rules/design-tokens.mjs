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
//
// Sprint 329 起，同一個 plugin 另有四條規則，把「視覺決定只能寫在 components/ui」變成制度（不只是文件）：
//   no-raw-control     — 禁止原生 <button>／<input>／<select>／<textarea>／<label>／<table> 系列（用 ui 元件）
//   no-native-dialog   — 禁止 alert()／confirm()／prompt()（用 toast／confirmDialog）
//   no-removed-utility — 禁止 Tailwind v3 已移除或專案沒定義的 class（bg-opacity-50、text-warning…）：它們不會報錯，只是靜靜地沒有效果
//   no-ui-recipe       — 禁止手刻「已經有元件」的配方（頁面標題、錯誤橫幅、載入／空狀態、Modal 遮罩、卡片）
// 後三者（除 no-native-dialog、no-removed-utility 全域適用外）的豁免範圍見 eslint.config.mjs：
// components/ui、components/layout、components/theme 可以寫視覺 class。新增元件時，把它取代的配方加進 no-ui-recipe 的 RECIPES。

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

// ---------------------------------------------------------------------------------------------
// Sprint 329：no-raw-control／no-native-dialog／no-removed-utility／no-ui-recipe
// ---------------------------------------------------------------------------------------------

const RAW_CONTROLS = {
  button: "<Button>（variant／size 已涵蓋實心、外框、連結樣式；可切換的選取態用 <Chip selected>）",
  input: "<Input>（checkbox／radio 用 <Checkbox>／<Radio>）",
  select: "<NativeSelect>（或 Radix 版的 <Select>）",
  textarea: "<Textarea>",
  label: "<Label>",
  table: "<Table>",
  thead: "<TableHeader>",
  tbody: "<TableBody>",
  tfoot: "<Table> 系列（沒有 TableFooter 就先加一個，不要退回原生元素）",
  tr: "<TableRow>",
  th: "<TableHead>",
  td: "<TableCell>",
}

const noRawControl = {
  meta: {
    type: "problem",
    docs: { description: "禁止原生表單／表格元素；視覺規格只寫在 components/ui。" },
    schema: [],
    messages: {
      raw: "禁止直接使用原生 <{{tag}}>：請改用 {{use}}。視覺規格（高度、框、圓角、字級、disabled 色）只寫在 components/ui，頁面只做語意與版面（見 docs/07_design/UI_DESIGN_SYSTEM_CONVENTIONS.md §9）。真有領域專用控制項（月曆日期格、星等）請在該行加 eslint-disable 並寫明理由。",
    },
  },
  create(context) {
    return {
      JSXOpeningElement(node) {
        if (node.name.type !== "JSXIdentifier") return
        const use = RAW_CONTROLS[node.name.name]
        if (use) context.report({ node, messageId: "raw", data: { tag: node.name.name, use } })
      },
    }
  },
}

const NATIVE_DIALOGS = new Set(["alert", "confirm", "prompt"])

const noNativeDialog = {
  meta: {
    type: "problem",
    docs: { description: "禁止原生 alert／confirm／prompt；用 toast 與 confirmDialog。" },
    schema: [],
    messages: {
      dialog:
        "禁止使用原生 {{name}}()：不能主題化、阻塞執行緒，而且 Playwright 會自動關閉未處理的對話框（confirm 回 false，破壞性流程在 E2E 走不到「確定」）。通知用 toast.success()／toast.error()（@/components/ui/toast），確認用 await confirmDialog(...)（@/components/ui/confirm-dialog）。",
    },
  },
  create(context) {
    // 只擋「全域的」alert／confirm／prompt；同名的區域變數（例如 alerts.map((alert) => …)）不是對話框
    const isGlobal = (node, name) => {
      for (let scope = context.sourceCode.getScope(node); scope; scope = scope.upper) {
        const v = scope.set.get(name)
        if (v && v.defs.length > 0) return false
      }
      return true
    }
    return {
      CallExpression(node) {
        const c = node.callee
        if (c.type === "Identifier" && NATIVE_DIALOGS.has(c.name) && isGlobal(node, c.name)) {
          context.report({ node, messageId: "dialog", data: { name: c.name } })
        } else if (
          c.type === "MemberExpression" &&
          c.object.type === "Identifier" &&
          c.object.name === "window" &&
          c.property.type === "Identifier" &&
          NATIVE_DIALOGS.has(c.property.name)
        ) {
          context.report({ node, messageId: "dialog", data: { name: `window.${c.property.name}` } })
        }
      },
    }
  },
}

// Tailwind v3 的 *-opacity-* 在 v4 已移除（bg-black bg-opacity-50 會變成純黑不透明）；
// shadcn 預設的 destructive／warning／info 語意色專案從沒定義過（text-warning 沒有任何樣式）。
// 這兩類都「不報錯、只是靜靜地沒有效果」，只有拿建置後的 CSS 對照原始碼才看得出來。
const REMOVED_OPACITY = new RegExp(
  `${BOUNDARY_BEFORE}${VARIANTS}(?:bg|text|border|ring|divide|placeholder)-opacity-\\d+${BOUNDARY_AFTER}`,
  "g"
)
const UNDEFINED_COLOR = new RegExp(
  `${BOUNDARY_BEFORE}${VARIANTS}(?:bg|text|border|ring|divide|fill|stroke|outline|decoration|accent|caret|from|to|via)-(?:destructive|warning|info)(?:-foreground)?(?:/\\d{1,3})?${BOUNDARY_AFTER}`,
  "g"
)

const noRemovedUtility = {
  meta: {
    type: "problem",
    docs: { description: "禁止 Tailwind v3 已移除或專案沒定義的 class（會靜靜地沒有效果）。" },
    schema: [],
    messages: {
      opacity:
        "「{{token}}」在 Tailwind v4 已移除，不會有任何效果（bg-black bg-opacity-50 會變成純黑）：請改用透明度語法，例如 bg-black/50。",
      color:
        "「{{token}}」沒有對應的顏色定義（globals.css 沒有 --color-destructive／warning／info），不會有任何效果：請改用設計稿 token——錯誤 rs-error、成功 rs-success、警告 rs-warning（亮黃，只能當底色／框色，文字用 text-rs-ink）、資訊 rs-info。",
    },
  },
  create(context) {
    function check(node, text) {
      if (!text || text.length < 6) return
      for (const m of text.matchAll(REMOVED_OPACITY)) context.report({ node, messageId: "opacity", data: { token: m[0] } })
      for (const m of text.matchAll(UNDEFINED_COLOR)) context.report({ node, messageId: "color", data: { token: m[0] } })
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

// 「配方→元件」登錄表：已經有元件的配方，不准再手刻。新增元件時在這裡加一列。
const hasAll = (toks, ...res) => res.every((re) => toks.some((t) => re.test(t)))
const setEq = (toks, want) => toks.length === want.length && want.every((w) => toks.includes(w))
const RECIPES = [
  {
    id: "banner",
    use: '<Alert variant="destructive|success|warning">',
    test: (t) =>
      // 錯誤／成功底色＋框，再加文字色或同色系的框色（有的橫幅文字色寫在內層，例如駁回原因）
      (hasAll(t, /^border$/, /^bg-(rs-)?(error|success)\/10$/) &&
        t.some((x) => /^(text-(rs-)?(error|success)|border-(rs-)?(error|success)\/\d+)$/.test(x))) ||
      hasAll(t, /^border$/, /^bg-rs-warning\/(10|15|20)$/, /^px-\d/),
  },
  {
    id: "badge",
    use: "<Badge>（狀態用 <StatusBadge domain status>，顏色與文字集中在 lib/status.ts）",
    // 手刻的小徽章／胶囊：text-xs ＋ 小內距 ＋ 圓角
    test: (t) => hasAll(t, /^text-xs$/, /^px-(1\.5|2)$/, /^py-(0\.5|1)$/, /^rounded(-full|-sm|-md)?$/),
  },
  { id: "loading", use: "<LoadingState />", test: (t) => setEq(t, ["flex", "items-center", "justify-center", "h-64"]) },
  { id: "empty", use: "<EmptyState title=… />", test: (t) => setEq(t, ["text-center", "py-12", "text-rs-ink-muted"]) },
  {
    id: "overlay",
    use: "<Dialog>（Radix：焦點鎖定、ESC、aria-modal、半透明遮罩）",
    test: (t) => t.includes("fixed") && t.includes("inset-0"),
  },
  {
    id: "card",
    use: "<Card>",
    test: (t) => hasAll(t, /^(bg-white|bg-rs-surface)$/, /^border$/, /^rounded-(lg|xl)$/), // 卡片是 12px（rounded-lg）；rounded-md（8px）是輸入框／按鈕這類控制項
  },
]

const noUiRecipe = {
  meta: {
    type: "problem",
    docs: { description: "禁止手刻『已經有元件』的樣式配方；視覺只寫在 components/ui。" },
    schema: [],
    messages: {
      recipe:
        "這串 class 是「{{id}}」的手刻配方，已有元件：請改用 {{use}}。要改外觀就改元件（components/ui），不是複製貼上到各頁。",
      title: '頁面標題請用 <PageTitle>（@/components/ui/page-title），不要手刻 <h1 className="text-2xl font-semibold …">。',
    },
  },
  create(context) {
    function check(node, text) {
      if (!text || text.length < 8) return
      const toks = text.split(/\s+/).filter(Boolean)
      for (const r of RECIPES) if (r.test(toks)) context.report({ node, messageId: "recipe", data: { id: r.id, use: r.use } })
    }
    return {
      Literal(node) {
        if (typeof node.value === "string") check(node, node.value)
      },
      TemplateElement(node) {
        check(node, node.value.raw)
      },
      JSXOpeningElement(node) {
        if (node.name.type !== "JSXIdentifier" || node.name.name !== "h1") return
        const attr = node.attributes.find((a) => a.type === "JSXAttribute" && a.name.name === "className")
        const v = attr && attr.value && attr.value.type === "Literal" ? attr.value.value : null
        if (typeof v === "string") {
          const toks = v.split(/\s+/)
          if (toks.includes("text-2xl") && (toks.includes("font-semibold") || toks.includes("font-bold"))) {
            context.report({ node, messageId: "title" })
          }
        }
      },
    }
  },
}

const designTokensPlugin = {
  rules: {
    "no-raw-palette": rule,
    "no-raw-control": noRawControl,
    "no-native-dialog": noNativeDialog,
    "no-removed-utility": noRemovedUtility,
    "no-ui-recipe": noUiRecipe,
  },
}

export default designTokensPlugin
