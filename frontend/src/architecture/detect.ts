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

/** 頁面標題：PageTitle 元件或原生 <h1> */
export const HEADING = /<PageTitle\b|<h1\b/

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
      if (target !== null && visit(target)) return true
    }
    return false
  }
  return visit(page)
}
