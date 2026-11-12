import { afterEach, describe, expect, it, vi } from 'vitest'
import { formatPublicDate, toLocalDateString } from './date'

// 反覆出現的缺陷（DEF-362 日期鎖誤判過去日、DEF-405 營收報表少算當天）：用 toISOString() 取「今天」，
// 在 UTC+8 的 00:00～07:59 會得到前一天。vitest.config.ts 把時區固定在 Asia/Taipei，所以這裡的測試在任何機器上
// 都會抓到這個錯誤（在 UTC 的 CI 上用 toISOString 與本地日期剛好相同，不固定時區就測不出來）。
describe('toLocalDateString：本地日曆日，不是 UTC 日', () => {
  it('測試環境確實在 UTC+8（否則下面的案例沒有鑑別力）', () => {
    expect(new Date(2026, 0, 1).getTimezoneOffset()).toBe(-480)
  })

  it('台灣凌晨 00:30：UTC 仍是前一天，但本地日曆日是當天', () => {
    const date = new Date(2026, 0, 1, 0, 30) // 本地 2026-01-01 00:30（= UTC 2025-12-31 16:30）
    expect(date.toISOString().startsWith('2025-12-31')).toBe(true) // 前提：這正是 toISOString 會錯的時間點
    expect(toLocalDateString(date)).toBe('2026-01-01')
  })

  it('台灣深夜 23:59：仍是當天', () => {
    expect(toLocalDateString(new Date(2026, 5, 15, 23, 59))).toBe('2026-06-15')
  })

  it('月份與日期補零成 YYYY-MM-DD（後端 LocalDate 與 <input type="date"> 都要這個格式）', () => {
    expect(toLocalDateString(new Date(2026, 2, 5))).toBe('2026-03-05')
    expect(toLocalDateString(new Date(2026, 11, 31))).toBe('2026-12-31')
  })
})

// 公開頁的內容改由伺服器渲染之後（SEO，DEF-500，Sprint 341），日期在「伺服器」與「訪客的瀏覽器」各算一次。
// 容器通常是 UTC、訪客在台灣（UTC+8）：UTC 16:00～24:00 之間發布的文章，沒指定時區的 toLocaleDateString 在兩邊會是不同的一天，
// React 偵測到文字不符就丟掉伺服器的 HTML 重新渲染（console 出現 hydration 錯誤）。這裡守的是「兩邊算出同一個字串」。
// （vitest.config.ts 把整個測試環境固定在 Asia/Taipei——所以這個缺陷在單元測試裡永遠看不到，要自己切時區。）
describe('formatPublicDate：公開頁的日期固定用台灣時區', () => {
  afterEach(() => vi.unstubAllEnvs())

  it('以台灣日曆日為準：UTC 15:59 還是 11/1、UTC 16:00 已經是台灣的 11/2', () => {
    expect(formatPublicDate('2026-11-01T15:59:59Z')).toBe('2026/11/1')
    expect(formatPublicDate('2026-11-01T16:00:00Z')).toBe('2026/11/2')
    expect(formatPublicDate('2026-11-01T20:00:00Z')).toBe('2026/11/2')
  })

  it('格式沿用原本的 zh-TW 短日期：年/月/日，月與日不補零', () => {
    expect(formatPublicDate('2026-01-05T04:00:00Z')).toBe('2026/1/5')
  })

  it('不受執行環境的時區影響（伺服器與瀏覽器各在不同時區，算出同一個字串）', () => {
    const instant = '2026-11-01T20:00:00Z'
    // 對照組：沒指定時區的寫法在不同時區就是不同的一天——這是要避開的缺陷；順便證明這個測試真的切得動時區（不是空轉）
    vi.stubEnv('TZ', 'UTC')
    const unsafeInUtc = new Date(instant).toLocaleDateString('zh-TW')
    vi.stubEnv('TZ', 'Asia/Taipei')
    const unsafeInTaipei = new Date(instant).toLocaleDateString('zh-TW')
    expect(unsafeInUtc, '測試環境必須真的切得了時區，否則下面的斷言沒有意義').not.toBe(unsafeInTaipei)

    for (const timeZone of ['UTC', 'Asia/Taipei', 'America/Los_Angeles', 'Pacific/Kiritimati', 'Europe/Berlin']) {
      vi.stubEnv('TZ', timeZone)
      expect(formatPublicDate(instant), `TZ=${timeZone}`).toBe('2026/11/2')
    }
  })
})
