// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { findSilentLoaders } from './detect'
import { listSourceFiles, readSource } from './source'

// 載入失敗要讓使用者看得見（DEF-468，Sprint 332）。
// Sprint 331 把 70 個頁面手寫的載入邏輯收成 useAsyncData 時，刻意保留了「失敗只 console.error」的原行為，
// 結果部落格、精選區塊等處的失敗被畫成「沒有資料」（使用者分不出沒有與壞了）。這條守門確保新的載入點不會再默默吞掉失敗：
// 每個 useAsyncData 呼叫點，失敗要嘛用 error／errorInfo 處理，要嘛 onError 用 toast 通知；真的不需要的要在這裡登記並寫理由。

/**
 * 失敗不會被使用者看見的呼叫點（每個檔案的數量與理由）。
 * 新增項目前先問：失敗時使用者會不會把畫面當成「沒有資料」？會的話改成顯示錯誤，不要登記。
 * 登記的是「輔助資訊」（失敗不影響頁面主要功能）或「已登記待修的缺陷」（理由寫 DEF 編號，修好就把它移出清單）。
 */
const ALLOWED: Record<string, { count: number; reason: string }> = {
  // ---- 輔助資訊：失敗只少一個選項或一個附屬區塊，頁面主要功能照常 ----
  'app/(storefront)/blog/page.tsx': {
    count: 1,
    reason:
      '分類篩選列是輔助資訊（Sprint 338，DEF-491 暫時入口，隨 DEF-495 一併處理）：失敗只少篩選列，文章列表本身失敗另有 blog-error 錯誤畫面；頁面沒有任何「分類」字樣，沒有篩選列不會被讀成「沒有分類」',
  },
  'app/admin/tenants/page.tsx': { count: 1, reason: '分頁籤上的數量是輔助資訊：失敗時維持上一次的數字；列表本身失敗另有錯誤畫面' },
  'app/dashboard/bookings/[id]/page.tsx': { count: 1, reason: '狀態日誌是輔助資訊：載入失敗不影響訂房詳情與入住／退房等主要功能' },
  'app/dashboard/faq/page.tsx': { count: 2, reason: '分類篩選的選項與置頂文章區塊是輔助資訊：失敗只少選項／少置頂區塊，文章列表本身失敗另有 toast' },
  'app/dashboard/knowledge/page.tsx': { count: 1, reason: '分類篩選的選項是輔助資訊：失敗只少選項，文章列表本身失敗另有 toast' },
  'app/dashboard/media/page.tsx': { count: 1, reason: '分類下拉的選項是輔助資訊：失敗只少選項，媒體列表本身失敗另有 toast' },
  'app/dashboard/posts/[id]/edit/page.tsx': {
    count: 1,
    reason: '嵌入商品卡預覽：失敗在 fetcher 內逐張處理，並在預覽區顯示「有 N 張無法載入」，hook 本身不會失敗',
  },
  // ---- 已登記待修的缺陷：失敗被畫成「沒有資料」或讓使用者卡住，修好就從這裡移除 ----
  // （目前沒有：Sprint 332 登記的 DEF-471～473 已在 Sprint 333 全部修好並移除）
}

describe('useAsyncData 的失敗要讓使用者看見（真實專案）', () => {
  const files = listSourceFiles(
    '.',
    (path) =>
      /^(app|components|hooks)\//.test(path) && /\.tsx?$/.test(path) && !/\.test\.tsx?$/.test(path) && path !== 'hooks/useAsyncData.ts'
  )
  const silent = new Map<string, number>()
  for (const file of files) {
    const hits = findSilentLoaders(readSource(file))
    if (hits.length > 0) silent.set(file, hits.length)
  }

  it('掃描範圍涵蓋全部頁面與元件', () => {
    expect(files.length).toBeGreaterThan(150)
  })

  it('沒有登記的檔案，不可以有靜默失敗的載入點', () => {
    const offenders = [...silent.entries()].filter(([file]) => !(file in ALLOWED)).map(([file, n]) => `${file}（${n} 處）`)
    expect(
      offenders,
      '這些載入點的失敗使用者看不到：解構 error／errorInfo 並顯示（或分流 404／403），或在 onError 用 toast.error。' +
        '失敗被畫成「沒有資料」比出錯畫面更糟——使用者不會重試'
    ).toEqual([])
  })

  it('已登記的檔案，靜默的數量不可增加（新增的載入點不能躲在舊登記底下）', () => {
    const grown = Object.entries(ALLOWED)
      .filter(([file, { count }]) => (silent.get(file) ?? 0) > count)
      .map(([file, { count }]) => `${file}：登記 ${count} 處，現在 ${silent.get(file)} 處`)
    expect(grown).toEqual([])
  })

  it('已登記的檔案若已修好（靜默數量變少或歸零），要同步把登記調降或移除（登記不是永久的）', () => {
    const stale = Object.entries(ALLOWED)
      .filter(([file, { count }]) => (silent.get(file) ?? 0) < count)
      .map(([file, { count }]) => `${file}：登記 ${count} 處，現在 ${silent.get(file) ?? 0} 處`)
    expect(stale).toEqual([])
  })

  it('每筆登記都有說得出口的理由', () => {
    for (const [file, { reason }] of Object.entries(ALLOWED)) {
      expect(reason.length, file).toBeGreaterThan(15)
    }
  })
})

describe('findSilentLoaders：偵測器本身', () => {
  const lines = (text: string) => findSilentLoaders(text).map((s) => s.line)

  it('解構 error 並且用到它 → 不算靜默', () => {
    expect(lines("const { data, error } = useAsyncData(() => load(), [], { errorMessage: 'x' })\nif (error) return null")).toEqual([])
  })

  it('解構了 error 卻從沒用到 → 算靜默（「有解構」不等於「有處理」）', () => {
    expect(lines("const { data, error } = useAsyncData(() => load(), [], { errorMessage: 'x' })\nreturn data")).toEqual([1])
  })

  it('改名的 error（error: loadError）要看改名後的識別字有沒有被用到；errorInfo 同理', () => {
    expect(lines("const { error: loadError } = useAsyncData(f, [], o)\nconst message = loadError ?? ''")).toEqual([])
    expect(lines("const { error: loadError } = useAsyncData(f, [], o)\nreturn 1")).toEqual([1])
    expect(lines("const { errorInfo } = useAsyncData(f, [], o)\nconst status = errorInfo?.status")).toEqual([])
  })

  it('onError 用 toast → 不算靜默；只有 console.error → 算靜默', () => {
    expect(lines("const { data } = useAsyncData(f, [], { errorMessage: 'x', onError: (m) => toast.error(m) })")).toEqual([])
    expect(lines("const { data } = useAsyncData(f, [], { errorMessage: 'x', onError: (_m, e) => console.error(e) })")).toEqual([1])
  })

  it('完全沒處理 → 靜默；沒有接回傳值的呼叫也是', () => {
    expect(lines("const { data } = useAsyncData(f, [], { errorMessage: 'x' })")).toEqual([1])
    expect(lines("useAsyncData(f, [], { errorMessage: 'x' })")).toEqual([1])
  })

  it('同一個檔案多個呼叫：只回報靜默的那幾個，行號正確', () => {
    const text = [
      "const { data: a } = useAsyncData(f, [], { errorMessage: 'x' })", // 1 靜默
      "const { data: b, error: bError } = useAsyncData(g, [], { errorMessage: 'y' })", // 2 有處理
      "const { data: c } = useAsyncData(h, [], { errorMessage: 'z', onError: (m) => toast.error(m) })", // 3 有處理
      "const { data: d } = useAsyncData(i, [], { errorMessage: 'w' })", // 4 靜默
      'if (bError) return null',
    ].join('\n')
    expect(lines(text)).toEqual([1, 4])
  })

  it('括號不成對丟錯，不靜默放行', () => {
    expect(() => findSilentLoaders("const { data } = useAsyncData(f, [], { errorMessage: 'x' ")).toThrow('括號不成對')
  })
})
