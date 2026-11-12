// 架構守門用的原始碼偵測器（純函式，輸入是檔案文字）。
//
// 為什麼不用 ESLint 規則：這幾條規則要看「一個 effect 本體＋同檔宣告的 async 函式」的組合，
// 寫成 ESLint 自訂規則比寫成測試貴很多，也不好讓人讀懂「為什麼這樣算違規」。偵測器本身有單元測試（見各 *.test.ts），
// 並在 Sprint 331 用突變驗證過（把偵測改壞，測試要紅）。
//
// 已知限制（刻意取捨）：只做括號計數，不解析字串、註解與正規表達式；括號不成對時直接丟錯，讓測試紅燈並指出檔案，不會靜默漏掉。

/** 從 text[openIndex]（必須是 ( 或 {）開始，回傳配對的收括號位置；括號不成對回傳 -1 */
export function matchingClose(text: string, openIndex: number): number {
  const open = text[openIndex]
  const close = open === '(' ? ')' : open === '{' ? '}' : ']'
  let depth = 0
  for (let i = openIndex; i < text.length; i++) {
    const c = text[i]
    if (c === open) depth++
    else if (c === close) {
      depth--
      if (depth === 0) return i
    }
  }
  return -1
}

/** 檔案裡每個 useEffect(...) 的括號內全文（含依賴陣列） */
export function effectBodies(text: string): string[] {
  const bodies: string[] = []
  for (const m of text.matchAll(/\buseEffect\(/g)) {
    const open = (m.index ?? 0) + m[0].length - 1
    const close = matchingClose(text, open)
    if (close < 0) throw new Error('useEffect( 的括號不成對，偵測器無法判斷')
    bodies.push(text.slice(open, close + 1))
  }
  return bodies
}

export interface AsyncFunction {
  name: string
  body: string
}

/**
 * 檔案裡「有名字」的 async 函式：
 *   async function name(...) { ... }
 *   const name = async (...) => { ... }
 *   const name = useCallback(async (...) => { ... }, [...])
 * 參數裡可以有大括號（例如 `signal?: { cancelled: boolean }`）：先把參數的小括號配對跳過，再找函式本體。
 */
export function asyncFunctions(text: string): AsyncFunction[] {
  const found: AsyncFunction[] = []
  const bodyAfterParams = (paramsOpen: number, expectArrow: boolean): string | null => {
    const paramsClose = matchingClose(text, paramsOpen)
    if (paramsClose < 0) throw new Error('async 函式參數的括號不成對，偵測器無法判斷')
    const rest = text.slice(paramsClose + 1)
    // 參數之後可有回傳型別標註；箭頭函式要有 `=>` 且本體用大括號（運算式本體不會有 setState 的語句）
    const head = (expectArrow ? /^\s*(?::\s*[^{=]+)?=>\s*\{/ : /^\s*(?::\s*[^{=]+)?\{/).exec(rest)
    if (!head) return null
    const braceIndex = paramsClose + 1 + head[0].length - 1
    const close = matchingClose(text, braceIndex)
    if (close < 0) throw new Error('async 函式本體的括號不成對，偵測器無法判斷')
    return text.slice(braceIndex, close + 1)
  }
  for (const m of text.matchAll(/async\s+function\s+(\w+)\s*\(/g)) {
    const body = bodyAfterParams((m.index ?? 0) + m[0].length - 1, false)
    if (body !== null) found.push({ name: m[1], body })
  }
  for (const m of text.matchAll(/(?:const|let)\s+(\w+)\s*=\s*(?:useCallback\(\s*)?async\s*\(/g)) {
    const body = bodyAfterParams((m.index ?? 0) + m[0].length - 1, true)
    if (body !== null) found.push({ name: m[1], body })
  }
  return found
}

const SETTER_CALL = /\bset[A-Z]\w*\(/

export interface LoadingFinding {
  kind: 'effect-sets-state-after-async' | 'effect-calls-async-function'
  detail: string
}

/**
 * 手寫的「在 effect 裡載入資料再 setState」（DEF-456 要消滅的樣板）。兩種形狀：
 *  1. effect 本體自己發請求再寫 state：本體同時有 setXxx( 與（.then( 或 await 或 async）；
 *  2. effect 呼叫同檔宣告的 async 函式，而那個函式裡有 setXxx(（loadXxx() 寫在 effect 外面也算）。
 * 只訂閱事件、只同步設定值（沒有 await／.then）的 effect 不算。
 */
export function findHandWrittenLoading(text: string): LoadingFinding[] {
  const findings: LoadingFinding[] = []
  const effects = effectBodies(text)
  for (const body of effects) {
    if (SETTER_CALL.test(body) && (/\.then\(/.test(body) || /\bawait\b/.test(body) || /\basync\b/.test(body))) {
      findings.push({ kind: 'effect-sets-state-after-async', detail: body.slice(0, 60).replace(/\s+/g, ' ') })
    }
  }
  for (const fn of asyncFunctions(text)) {
    if (!SETTER_CALL.test(fn.body)) continue
    // 宣告在 effect 裡面的 async 函式，上一段已經以「effect 本體」算過了，不重複回報
    if (effects.some((body) => body.includes(fn.body))) continue
    const calledByEffect = effects.some((body) => new RegExp(`\\b${fn.name}\\b`).test(body))
    if (calledByEffect) findings.push({ kind: 'effect-calls-async-function', detail: fn.name })
  }
  return findings
}

export interface SilentLoader {
  /** useAsyncData( 所在行（1 起算） */
  line: number
}

/**
 * 「載入失敗沒有被使用者看見」的 useAsyncData 呼叫點（DEF-468）。一個呼叫點算「有顯示失敗」要滿足其一：
 *  1. 解構出 error 或 errorInfo（可以改名），而且那個識別字在檔案其餘地方（不含解構敘述本身）還有被用到；
 *  2. options 的 onError 本體呼叫 toast.（失敗用 toast 通知）。
 * 兩者都沒有就是靜默：失敗時畫面只會是「沒有資料」或空白，使用者分不出是真的沒有還是壞了。
 *
 * 已知限制（刻意取捨）：「有用到 error」不代表有「顯示」——例如把它拿來把失敗當成空清單（`error ? [] : data`）也算有用到，
 * 這種形狀偵測不到（Sprint 332 的 StoreFeaturedListings 就是，靠元件測試守）。這條守門只擋最明顯的一類：完全沒處理失敗。
 */
export function findSilentLoaders(text: string): SilentLoader[] {
  const found: SilentLoader[] = []
  for (const m of text.matchAll(/\buseAsyncData\(/g)) {
    const index = m.index ?? 0
    const open = index + m[0].length - 1
    const close = matchingClose(text, open)
    if (close < 0) throw new Error('useAsyncData( 的括號不成對，偵測器無法判斷')
    const args = text.slice(open, close + 1)
    const headStart = Math.max(0, index - 400)
    const head = text.slice(headStart, index)
    const decl = /(?:const|let)\s*(\{[^}]*\}|\w+)\s*=\s*(?:await\s*)?$/.exec(head)
    const destructured = decl ? decl[1] : ''
    // 去掉解構敘述與呼叫本身，剩下的才是「其餘地方」
    const declStart = decl ? headStart + decl.index : index
    const rest = text.slice(0, declStart) + ' ' + text.slice(close + 1)
    let surfaced = false
    for (const key of ['error', 'errorInfo']) {
      const k = new RegExp(`\\b${key}\\b\\s*(?::\\s*(\\w+))?`).exec(destructured)
      if (!k) continue
      const name = k[1] ?? key
      if (new RegExp(`\\b${name}\\b`).test(rest)) surfaced = true
    }
    const onError = /onError\s*:/.exec(args)
    if (!surfaced && onError && args.slice(onError.index).includes('toast.')) surfaced = true
    if (!surfaced) found.push({ line: text.slice(0, index).split('\n').length })
  }
  return found
}

/** 頁面標題：PageTitle 元件或原生 <h1> */
export const HEADING = /<PageTitle\b|<h1\b/

/**
 * PageTitle 元件自己的定義檔：它裡面當然有 <h1>，但「只是 import 它」不代表檔案真的渲染了標題。
 * 追 import 時不能算（Sprint 331 突變驗證 G09 發現：不排除的話，任何檔案只要留著 PageTitle 的 import——即使已經沒在用——
 * 就會透過它「有標題」，守門等於對這種情形失明）。
 */
export const HEADING_DEFINITION = 'components/ui/page-title.tsx'

const IMPORT_SPECIFIER = /(?:import|export)\s[^;]*?from\s+['"]([^'"]+)['"]/g

/**
 * 把 import 路徑解析成 src 內的 .tsx 檔（相對 src 的 POSIX 路徑）；不是 src 內的元件就回傳 null。
 * 只追 components／app 底下的 .tsx（services、lib 不會渲染標題）。
 */
export function resolveComponentImport(
  fromFile: string,
  specifier: string,
  exists: (path: string) => boolean
): string | null {
  let base: string
  if (specifier.startsWith('@/')) {
    base = specifier.slice(2)
  } else if (specifier.startsWith('.')) {
    const parts = fromFile.split('/').slice(0, -1)
    for (const segment of specifier.split('/')) {
      if (segment === '.' || segment === '') continue
      if (segment === '..') parts.pop()
      else parts.push(segment)
    }
    base = parts.join('/')
  } else {
    return null
  }
  for (const candidate of [`${base}.tsx`, `${base}/index.tsx`]) {
    if ((candidate.startsWith('components/') || candidate.startsWith('app/')) && exists(candidate)) return candidate
  }
  return null
}

/** 這個頁面自己，或它（傳遞式）引用的元件，有沒有頁面標題 */
export function hasHeadingTransitively(
  page: string,
  read: (path: string) => string,
  exists: (path: string) => boolean
): boolean {
  const visited = new Set<string>()
  const visit = (file: string): boolean => {
    if (visited.has(file)) return false
    visited.add(file)
    const text = read(file)
    if (HEADING.test(text)) return true
    for (const m of text.matchAll(IMPORT_SPECIFIER)) {
      const target = resolveComponentImport(file, m[1], exists)
      if (target !== null && target !== HEADING_DEFINITION && visit(target)) return true
    }
    return false
  }
  return visit(page)
}

export interface LinkElement {
  /** `<Link` 所在行（1 起算） */
  line: number
  /** href 屬性的內容（運算式或字串，不含最外層的大括號／引號）；`href={某個識別字}` 會換成同檔 const／let 宣告的右式；沒有 href 就是空字串 */
  href: string
  /** 開頭標籤裡有沒有明確寫 `prefetch={false}` */
  noPrefetch: boolean
}

/** 從 from（`<Link` 之後）開始，找開頭標籤結尾的 `>`；屬性的 {…} 運算式與 "…"／'…' 字串整段跳過（`=>` 與字串裡的 `>` 不算） */
function openingTagEnd(text: string, from: number): number {
  let i = from
  while (i < text.length) {
    const c = text[i]
    if (c === '{') {
      const close = matchingClose(text, i)
      if (close < 0) throw new Error('<Link 屬性的大括號不成對，偵測器無法判斷')
      i = close + 1
    } else if (c === '"' || c === "'") {
      const close = text.indexOf(c, i + 1)
      if (close < 0) throw new Error('<Link 屬性的引號不成對，偵測器無法判斷')
      i = close + 1
    } else if (c === '>') {
      return i
    } else {
      i++
    }
  }
  throw new Error('<Link 的開頭標籤沒有結束，偵測器無法判斷')
}

function hrefOf(text: string, attrs: string): string {
  const m = /\bhref\s*=\s*/.exec(attrs)
  if (!m) return ''
  const at = m.index + m[0].length
  const c = attrs[at]
  let value = ''
  if (c === '{') {
    const close = matchingClose(attrs, at)
    if (close < 0) throw new Error('<Link 的 href 大括號不成對，偵測器無法判斷')
    value = attrs.slice(at + 1, close).trim()
  } else if (c === '"' || c === "'") {
    const close = attrs.indexOf(c, at + 1)
    if (close < 0) throw new Error('<Link 的 href 引號不成對，偵測器無法判斷')
    value = attrs.slice(at + 1, close)
  }
  // href={blogHref}：換成同檔宣告的右式（const blogHref = `/stores/${slug}/blog`），才看得出它指向哪裡
  if (/^[A-Za-z_$][\w$]*$/.test(value)) {
    const decl = new RegExp(`\\b(?:const|let)\\s+${value}\\s*=\\s*([^;\\n]+)`).exec(text)
    if (decl) return decl[1].trim()
  }
  return value
}

/**
 * 檔案裡每個 `<Link …>` 的開頭標籤（不含 `<LinkButton` 這類名稱更長的元件）。
 *
 * 已知限制（刻意取捨）：只認得 JSX 的 `<Link`，不分它是不是 next/link 的（專案裡沒有別的叫 Link 的元件）；
 * 括號計數同 matchingClose，不解析字串裡的括號；`prefetch` 只認字面的 `{false}`（寫成 `{cond ? false : null}` 會被當成沒關）。
 */
export function linkElements(text: string): LinkElement[] {
  const found: LinkElement[] = []
  for (const m of text.matchAll(/<Link(?=[\s/>])/g)) {
    const start = m.index ?? 0
    const attrs = text.slice(start, openingTagEnd(text, start + m[0].length))
    found.push({
      line: text.slice(0, start).split('\n').length,
      href: hrefOf(text, attrs),
      noPrefetch: /\bprefetch\s*=\s*\{\s*false\s*\}/.test(attrs),
    })
  }
  return found
}

/**
 * href 是不是指向「伺服器端 generateMetadata 會打後端」的實體頁：/stores/…（店鋪頁、貼文列表、文章頁）與 /listings/…（商品／房源詳情），
 * 或產生這類網址的輔助函式（articlePath、storeBlogPath）。必須是網址的開頭（`/admin/stores/…` 這類後台路徑不算）。
 */
export function isEntityHref(href: string): boolean {
  return /^\s*[`'"]\/(?:stores|listings)\//.test(href) || /^\s*(?:articlePath|storeBlogPath)\(/.test(href)
}
