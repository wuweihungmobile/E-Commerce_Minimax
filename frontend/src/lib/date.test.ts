import { describe, expect, it } from 'vitest'
import { toLocalDateString } from './date'

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
