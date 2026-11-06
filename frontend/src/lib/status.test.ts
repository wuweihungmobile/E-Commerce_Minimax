import { describe, expect, it } from 'vitest'
import { STATUS_REGISTRY, featureStatusEntry, statusEntry, type StatusVariant } from './status'

// Sprint 329 之前，同一個概念由 16 個檔案各自決定顏色且互相矛盾（貼文「草稿」灰、知識庫「草稿」黃）。
// 顏色是業務語意，不是各頁的裝飾——這裡的測試守的是「語意規則」，不是某個標籤的某個顏色。
describe('STATUS_REGISTRY：同一種語意在所有領域是同一個顏色', () => {
  // 規則寫在 status.ts 檔頭；關鍵字比對標籤，所以新增領域時新增的狀態也會被檢查。
  const RULES: Array<{ meaning: string; match: RegExp; variant: StatusVariant }> = [
    { meaning: '草稿＝中性、尚未完成', match: /草稿/, variant: 'outline' },
    { meaning: '駁回／取消／停權／終止／拒絕＝負面結果', match: /駁回|取消|停權|終止|拒絕/, variant: 'destructive' },
    { meaning: '停用／封存／歸檔／下架＝中性、不再生效', match: /停用|封存|歸檔|下架/, variant: 'secondary' },
    { meaning: '待審／審批中／部分到貨＝需要有人處理', match: /待審|審批|審核中|部分到貨/, variant: 'warning' },
    { meaning: '啟用／已發布／已核准／已到貨／上架中＝正面結果', match: /啟用|已發布|已核准|已到貨|上架中/, variant: 'success' },
  ]

  const entries = Object.entries(STATUS_REGISTRY).flatMap(([domain, table]) =>
    Object.entries(table).map(([status, entry]) => ({ domain, status, ...entry }))
  )

  it('登錄表不是空的（避免規則迴圈對空集合「恆真」）', () => {
    expect(entries.length).toBeGreaterThan(20)
  })

  it.each(RULES)('$meaning', ({ match, variant }) => {
    const hits = entries.filter((e) => match.test(e.label))
    // 規則至少要涵蓋到一個登錄項目，否則這條規則沒有守任何東西
    expect(hits.length).toBeGreaterThan(0)
    for (const e of hits) {
      expect(e.variant, `${e.domain}.${e.status}「${e.label}」`).toBe(variant)
    }
  })
})

describe('statusEntry', () => {
  it('查得到就回登錄的標籤與顏色', () => {
    expect(statusEntry('post', 'PUBLISHED')).toEqual({ label: '已發布', variant: 'success' })
  })

  it('沒登錄的狀態值原樣顯示（不吞掉後端新增的狀態，讓使用者與開發者看得到它存在）', () => {
    expect(statusEntry('post', 'BRAND_NEW_STATUS')).toEqual({ label: 'BRAND_NEW_STATUS', variant: 'outline' })
  })
})

describe('featureStatusEntry：功能開關狀態', () => {
  // DEF-359：待審核時 isEnabled 本身就是 false（DEF-247 既有設計）。若先判斷 !enabled，
  // 「待審核」會被標成「已停用」，看起來像已被駁回。
  it('需要管理員審核且為 PENDING → 待審核（即使 enabled=false）', () => {
    expect(featureStatusEntry('PENDING', true, false)).toEqual({ label: '待審核', variant: 'warning' })
  })

  it('不需要管理員審核的 PENDING 不會顯示待審核', () => {
    expect(featureStatusEntry('PENDING', false, false).label).toBe('已停用')
  })

  it('停用優先於其他狀態：enabled=false → 已停用', () => {
    expect(featureStatusEntry('ACTIVE', false, false)).toEqual({ label: '已停用', variant: 'secondary' })
  })

  it('啟用且 ACTIVE → 已啟用', () => {
    expect(featureStatusEntry('ACTIVE', false, true)).toEqual({ label: '已啟用', variant: 'success' })
  })

  it('啟用但狀態不認得 → 原樣顯示狀態值、用中性色', () => {
    expect(featureStatusEntry('SOMETHING', false, true)).toEqual({ label: 'SOMETHING', variant: 'secondary' })
  })
})
