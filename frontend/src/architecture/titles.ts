// 頁面標題（Next 的 metadata API）的靜態偵測與解析（Sprint 332，DEF-465）。純函式，輸入是檔案文字。
//
// 為什麼要「模擬 Next 的解析」而不是只檢查「有沒有 export metadata」：標題是由根 layout → 各層 layout → 頁面
// 一路解析出來的，而 Next 的規則有一條很容易踩到——中間層的標題如果沒有 `template`，會把上層傳下來的 template 切斷
// （Sprint 332 開發時實際發生：`bookings/[id]/layout.tsx` 設了字串標題，結果它底下的 `payment/cancel` 頁面標題少了品牌後綴，
// 只有開瀏覽器才看得到，tsc、eslint、build 都沒有任何訊號）。所以守門要算出「每一頁最終的標題」再檢查。
//
// 模擬的規則取自 Next 的 resolve-title：
//   - 字串標題：套用上層傳下來的 template；它自己不帶 template，所以底下的層級拿到的 template 是「沒有」。
//   - { default, template }：default 套用上層的 template；template 傳給底下的層級。
//   - { absolute }：直接用，不套 template；底下的層級同樣拿不到 template。
//   - 沒有標題的層級：不改變任何東西（沿用上一個標題與 template）。
// 這個模擬本身由真實瀏覽器的 E2E（at-page-titles.spec.ts）與全路由探針對照過——模擬與 Next 不一致時，以瀏覽器為準。
//
// 已知限制（刻意取捨）：只認 `title: '字串'`、`title: { default, template }`、`title: { absolute }` 三種寫法，值可以是字串字面值或
// 常數名稱（常數由呼叫端提供，見 lib/site.ts）；`generateMetadata` 只能確認「有」，算不出內容——解析時以 DYNAMIC_TITLE_PROBE 代入
// （假設它回傳一般的字串標題，會接上上層的 template；Sprint 335 起公開頁的實體標題就是這樣，見 lib/seo.ts），所以『template 有沒有被中間層切斷』
// 仍然查得到，只是動態標題的實際內容不參與『彼此不重複』的比對。不認得的寫法直接丟錯，讓測試紅燈並指出檔案，不會靜默放行。

import { matchingClose } from './detect'

/** 動態標題（generateMetadata）解析時代入的探針字串：用來確認標題有接上品牌 template，並讓呼叫端認得「這是動態的」。 */
export const DYNAMIC_TITLE_PROBE = '〈動態標題〉'

export type TitleSpec =
  | { kind: 'string'; text: string }
  | { kind: 'object'; default?: string; template?: string; absolute?: string }
  | { kind: 'dynamic' }

type Constants = Record<string, string>

function parseValue(raw: string, constants: Constants, where: string): string {
  const value = raw.trim()
  const quoted = /^(['"`])([\s\S]*)\1$/.exec(value)
  if (quoted) {
    if (quoted[2].includes('${')) throw new Error(`${where}：標題字串含 \${} 運算式，偵測器無法判斷：${value.slice(0, 60)}`)
    return quoted[2]
  }
  if (/^[A-Za-z_]\w*$/.test(value)) {
    if (!(value in constants)) throw new Error(`${where}：不認得的常數 ${value}（請加進呼叫端提供的常數表）`)
    return constants[value]
  }
  throw new Error(`${where}：標題的值不是字串字面值也不是常數名稱：${value.slice(0, 60)}`)
}

/**
 * 從 text[start] 起讀一個「值」：字串字面值（到配對的引號）或識別字；回傳原文與結束位置。
 * 值之後必須緊接逗號或結束括號：否則是運算式（`'a' + 'b'`、`cond ? 'a' : 'b'`），只讀到第一段會算出錯的標題卻不報錯。
 */
function readValue(text: string, start: number, where: string): { raw: string; end: number } {
  let i = start
  while (/\s/.test(text[i])) i++
  const first = text[i]
  let raw: string
  let end: number
  if (first === "'" || first === '"' || first === '`') {
    let j = i + 1
    while (j < text.length && text[j] !== first) j += text[j] === '\\' ? 2 : 1
    raw = text.slice(i, j + 1)
    end = j + 1
  } else {
    const m = /^[A-Za-z_]\w*/.exec(text.slice(i))
    if (!m) return { raw: text.slice(i, i + 40), end: i }
    raw = m[0]
    end = i + m[0].length
  }
  const rest = text.slice(end).trimStart()
  if (rest !== '' && !/^[,}]/.test(rest)) {
    throw new Error(`${where}：標題的值是運算式（值之後不是逗號或結束括號），偵測器無法判斷：${text.slice(i, i + 50)}`)
  }
  return { raw, end }
}

/** 物件文字（含外層大括號）裡，深度 1 的 `key:` 之後的位置；找不到回 -1（不會誤抓巢狀物件裡的同名鍵） */
function topLevelKey(objectText: string, key: string): number {
  let depth = 0
  let quote: string | null = null
  for (let i = 0; i < objectText.length; i++) {
    const c = objectText[i]
    if (quote) {
      if (c === '\\') i++
      else if (c === quote) quote = null
      continue
    }
    if (c === "'" || c === '"' || c === '`') quote = c
    else if (c === '{' || c === '[' || c === '(') depth++
    else if (c === '}' || c === ']' || c === ')') depth--
    else if (depth === 1 && objectText.startsWith(key, i) && !/\w/.test(objectText[i - 1] ?? ' ')) {
      const after = /^\s*:/.exec(objectText.slice(i + key.length))
      if (after) return i + key.length + after[0].length
    }
  }
  return -1
}

/** 檔案有沒有匯出標題；沒有回 null。不認得的寫法丟錯。 */
export function findTitleSpec(text: string, constants: Constants, where: string): TitleSpec | null {
  if (/export\s+(?:async\s+)?function\s+generateMetadata\b/.test(text)) return { kind: 'dynamic' }
  const decl = /export\s+const\s+metadata\b[^=]*=\s*\{/.exec(text)
  if (!decl) return null
  const open = decl.index + decl[0].length - 1
  const close = matchingClose(text, open)
  if (close < 0) throw new Error(`${where}：metadata 物件的括號不成對，偵測器無法判斷`)
  const object = text.slice(open, close + 1)
  const titleAt = topLevelKey(object, 'title')
  if (titleAt < 0) return null // 有 metadata 但沒有標題（例如只有 description）
  let i = titleAt
  while (/\s/.test(object[i])) i++
  if (object[i] === '{') {
    const end = matchingClose(object, i)
    if (end < 0) throw new Error(`${where}：title 物件的括號不成對，偵測器無法判斷`)
    const inner = object.slice(i, end + 1)
    const spec: TitleSpec = { kind: 'object' }
    for (const key of ['default', 'template', 'absolute'] as const) {
      const at = topLevelKey(inner, key)
      if (at >= 0) spec[key] = parseValue(readValue(inner, at, `${where} title.${key}`).raw, constants, `${where} title.${key}`)
    }
    if (spec.default === undefined && spec.absolute === undefined && spec.template === undefined) {
      throw new Error(`${where}：title 物件裡沒有 default／template／absolute`)
    }
    return spec
  }
  return { kind: 'string', text: parseValue(readValue(object, i, `${where} title`).raw, constants, `${where} title`) }
}

const apply = (template: string | null, title: string) => (template ? template.replaceAll('%s', title) : title)

/**
 * 依序（根 layout → 底下各層 layout → 頁面）解析出頁面最終的標題。chain 只放「有標題」的層級。
 * 回傳 null＝整條鏈上沒有任何標題。
 */
export function resolveTitle(chain: TitleSpec[]): string | null {
  let stash: string | null = null
  let current: string | null = null
  for (const spec of chain) {
    if (spec.kind === 'string' || spec.kind === 'dynamic') {
      // 動態標題假設是一般字串標題：接上上層的 template，並把 template 切斷（與靜態字串標題相同）
      current = apply(stash, spec.kind === 'dynamic' ? DYNAMIC_TITLE_PROBE : spec.text)
      stash = null // 字串標題把 template 切斷
    } else {
      if (spec.absolute !== undefined) current = spec.absolute
      else if (spec.default !== undefined) current = apply(stash, spec.default)
      stash = spec.template ?? null
    }
  }
  return current
}

/** 頁面（src 內的 app/.../page.tsx）從根到頁面的標題來源檔案：每一層目錄的 layout.tsx，最後是頁面本身 */
export function titleSourceFiles(pageFile: string): string[] {
  const parts = pageFile.split('/').slice(0, -1) // app, a, b...
  const files: string[] = []
  for (let n = 1; n <= parts.length; n++) files.push([...parts.slice(0, n), 'layout.tsx'].join('/'))
  files.push(pageFile)
  return files
}
