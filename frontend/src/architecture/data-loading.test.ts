// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { asyncFunctions, effectBodies, findHandWrittenLoading, matchingClose } from './detect'
import { listSourceFiles, readSource } from './source'

// DEF-456 守門（Sprint 331，ADR-002）：頁面與元件載入資料一律走 useAsyncData，不再各自手寫
// 「useState(loading／error／data) ＋ useEffect 裡 fetch 再 setState」。
//
// Sprint 331 之前這個樣板在約 80 個檔案各寫一次，其中一半沒有處理競態（篩選條件連續改變時，慢回來的舊回應蓋掉新結果）。
// 光靠 code review 擋不住下一個人複製貼上，所以把規則寫成測試：新增手寫載入 → npm test 紅燈。

/**
 * 允許 effect 內「await／.then 之後 setState」的檔案，每一筆都要寫理由（為什麼它不是「載入資料」）。
 * 清單只該縮短不該變長：新頁面要載入資料請用 useAsyncData（src/hooks/useAsyncData.ts）。
 */
const ALLOWED: Record<string, string> = {
  'app/oauth/callback/[provider]/page.tsx':
    '一次性登入流程：消耗只能用一次的 OAuth state、以授權碼換 token、導頁。不是載入資料；被中止或重跑會讓登入失敗。',
  'app/(auth)/verify-email/page.tsx':
    '一次性動作：以信裡的 token 送出信箱驗證，用 ref 擋掉 StrictMode 的重複送出。不是載入資料。',
  'components/ui/authenticated-image.tsx':
    '管理 blob object URL 的生命週期（建立與 revoke），是資源管理；effect 的清理函式必須 revoke，hook 的資料模型放不進去。',
  'components/layout/StorefrontHeader.tsx':
    '訂閱「購物車變更」事件，事件一來就重抓數量；重抓期間要保留舊數字（不閃回 0），失敗不影響頁首。是事件訂閱，不是頁面載入。',
}

function isScanned(path: string): boolean {
  if (!/\.tsx?$/.test(path) || /\.test\.tsx?$/.test(path)) return false
  if (path === 'hooks/useAsyncData.ts') return false // 實作本身
  return path.startsWith('app/') || path.startsWith('components/') || path.startsWith('hooks/')
}

describe('資料載入一律走 useAsyncData（DEF-456 守門）', () => {
  const files = listSourceFiles('', isScanned)

  it('掃描範圍涵蓋頁面、元件與 hooks（不是掃到零個檔案而假綠燈）', () => {
    expect(files.length).toBeGreaterThan(150)
    expect(files).toContain('app/dashboard/orders/page.tsx')
    expect(files).toContain('components/layout/StorefrontHeader.tsx')
  })

  it('沒有手寫的「在 effect 裡載入資料再 setState」（例外見 ALLOWED，每一筆都有理由）', () => {
    const violations = files
      .filter((file) => !(file in ALLOWED))
      .flatMap((file) =>
        findHandWrittenLoading(readSource(file)).map((finding) => `${file}：${finding.kind}（${finding.detail}）`)
      )
    expect(
      violations,
      '請改用 useAsyncData；確實不是「載入資料」的才加進 ALLOWED 並寫理由（見 ADR-002）'
    ).toEqual([])
  })

  it('ALLOWED 的每一筆都還在命中（改好了就從清單移除，清單不能只增不減）', () => {
    const stale = Object.keys(ALLOWED).filter(
      (file) => !files.includes(file) || findHandWrittenLoading(readSource(file)).length === 0
    )
    expect(stale).toEqual([])
  })

  it('每一筆例外都寫了理由', () => {
    for (const [file, reason] of Object.entries(ALLOWED)) {
      expect(reason.length, file).toBeGreaterThan(15)
    }
  })
})

describe('偵測器本身（規則的意圖）', () => {
  const effectLoader = `
    useEffect(() => {
      let cancelled = false
      fetchThings().then((r) => { if (!cancelled) setThings(r) })
      return () => { cancelled = true }
    }, [])`

  it('effect 內 .then 之後 setState：違規（最常見的手寫載入）', () => {
    expect(findHandWrittenLoading(effectLoader).map((f) => f.kind)).toEqual(['effect-sets-state-after-async'])
  })

  it('effect 內宣告 async 函式、await 之後 setState：違規', () => {
    const code = `
      useEffect(() => {
        async function load() { const r = await getThings(); setThings(r) }
        load()
      }, [])`
    expect(findHandWrittenLoading(code).map((f) => f.kind)).toEqual(['effect-sets-state-after-async'])
  })

  it('載入函式寫在 effect 外面、effect 只呼叫它：違規（loadXxx 的老寫法）', () => {
    const code = `
      async function loadThings() { const r = await getThings(); setThings(r) }
      useEffect(() => { loadThings() }, [])`
    expect(findHandWrittenLoading(code)).toEqual([{ kind: 'effect-calls-async-function', detail: 'loadThings' }])
  })

  it('參數型別裡有大括號的 useCallback(async (signal?: { cancelled: boolean }) => …)：照樣偵測到（曾是偵測器的盲點）', () => {
    const code = `
      const load = useCallback(
        async (signal?: { cancelled: boolean }) => {
          const r = await getThings()
          if (signal?.cancelled) return
          setThings(r)
        },
        [id]
      )
      useEffect(() => { const signal = { cancelled: false }; load(signal) }, [load])`
    expect(findHandWrittenLoading(code)).toEqual([{ kind: 'effect-calls-async-function', detail: 'load' }])
  })

  it('async 函式只被事件處理器呼叫（沒被 effect 呼叫）：不算違規（送出表單、按鈕動作）', () => {
    const code = `
      async function handleSubmit() { await save(); setSaved(true) }
      useEffect(() => { document.title = 'x' }, [])`
    expect(findHandWrittenLoading(code)).toEqual([])
  })

  it('effect 只訂閱事件、沒有 await／.then：不算違規', () => {
    const code = `
      useEffect(() => {
        const onResize = () => setWidth(window.innerWidth)
        window.addEventListener('resize', onResize)
        return () => window.removeEventListener('resize', onResize)
      }, [])`
    expect(findHandWrittenLoading(code)).toEqual([])
  })

  it('用了 useAsyncData 的頁面：不算違規', () => {
    const code = `
      const { data, loading } = useAsyncData(() => getThings(), [], { errorMessage: '失敗' })
      useEffect(() => { if (data) document.title = data.name }, [data])`
    expect(findHandWrittenLoading(code)).toEqual([])
  })

  it('括號不成對：丟錯而不是靜默漏掉（測試紅燈會指出問題檔案）', () => {
    expect(() => effectBodies('useEffect(() => { setX(1)')).toThrow('括號不成對')
    expect(() => asyncFunctions('async function f(a { return 1 }')).toThrow('括號不成對')
  })

  it('matchingClose 配對巢狀括號', () => {
    expect(matchingClose('f(a(b)c)d', 1)).toBe(7)
    expect(matchingClose('{ { } }', 0)).toBe(6)
  })
})
