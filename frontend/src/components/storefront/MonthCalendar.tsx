"use client"

import { useEffect, useState } from "react"
import bookingService, { type CalendarDay, type RoomCalendarStatus } from "@/services/booking"

// 整月日曆（Sprint 41 US-004 / AI-2202b）：顯示房源某月每日可訂狀況，
// 並支援點選挑選入住／退房區間，與 ListingDetail 的 checkIn/checkOut 同步。

const WEEKDAYS = ["日", "一", "二", "三", "四", "五", "六"]
const UNAVAILABLE_STATUS: ReadonlySet<RoomCalendarStatus> = new Set<RoomCalendarStatus>([
  "BOOKED",
  "BLOCKED",
  "MAINTENANCE",
])

function pad(n: number): string {
  return n < 10 ? "0" + n : String(n)
}

function iso(year: number, month0: number, day: number): string {
  return `${year}-${pad(month0 + 1)}-${pad(day)}`
}

function daysInMonth(year: number, month0: number): number {
  return new Date(year, month0 + 1, 0).getDate()
}

// 日曆格價格（精簡：TWD 用 $，其餘用幣別碼前綴）
function formatCellPrice(currency: string, value: number): string {
  const symbol = currency === "TWD" ? "$" : currency + " "
  return symbol + value.toLocaleString("zh-TW")
}

interface MonthCalendarProps {
  roomListingId: string
  checkIn: string
  checkOut: string
  basePrice: number
  currency: string
  onSelectRange: (checkIn: string, checkOut: string) => void
}

export function MonthCalendar({
  roomListingId,
  checkIn,
  checkOut,
  basePrice,
  currency,
  onSelectRange,
}: MonthCalendarProps) {
  const now = new Date()
  const todayIso = iso(now.getFullYear(), now.getMonth(), now.getDate())

  const [view, setView] = useState(() => {
    // 初始顯示月份：以已選入住日，否則今日
    const base = checkIn ? new Date(checkIn + "T00:00:00") : now
    return { year: base.getFullYear(), month: base.getMonth() }
  })
  const [statusByDate, setStatusByDate] = useState<Record<string, RoomCalendarStatus>>({})
  const [priceByDate, setPriceByDate] = useState<Record<string, number>>({})
  // 每日折扣前原價（AI-2405b）：有折扣之日才有值，用於顯示刪除線原價
  const [originalPriceByDate, setOriginalPriceByDate] = useState<Record<string, number>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // 載入可視月份的日曆（async；不在 effect 內同步 setState — 遵循 React 19 嚴格 hooks）
  useEffect(() => {
    let cancelled = false
    const start = iso(view.year, view.month, 1)
    const end = iso(view.year, view.month, daysInMonth(view.year, view.month))
    bookingService
      .getCalendar(roomListingId, start, end)
      .then((days: CalendarDay[]) => {
        if (cancelled) return
        const statusMap: Record<string, RoomCalendarStatus> = {}
        const priceMap: Record<string, number> = {}
        const originalMap: Record<string, number> = {}
        for (const d of days) {
          statusMap[d.date] = d.status
          if (d.price != null) priceMap[d.date] = d.price
          if (d.originalPrice != null) originalMap[d.date] = d.originalPrice
        }
        setStatusByDate(statusMap)
        setPriceByDate(priceMap)
        setOriginalPriceByDate(originalMap)
        setError(null)
      })
      .catch(() => {
        if (!cancelled) setError("日曆載入失敗，仍可手動輸入日期")
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [roomListingId, view.year, view.month])

  const isUnavailable = (dateStr: string): boolean => {
    if (dateStr < todayIso) return true
    const s = statusByDate[dateStr]
    return s ? UNAVAILABLE_STATUS.has(s) : false
  }

  // [start, end) 之間（每一夜）是否含不可訂日
  const rangeHasBlocked = (startStr: string, endStr: string): boolean => {
    const d = new Date(startStr + "T00:00:00")
    const endD = new Date(endStr + "T00:00:00")
    while (d < endD) {
      if (isUnavailable(iso(d.getFullYear(), d.getMonth(), d.getDate()))) return true
      d.setDate(d.getDate() + 1)
    }
    return false
  }

  const handleDayClick = (dateStr: string) => {
    if (isUnavailable(dateStr)) return
    // 尚未選入住、或已選完整區間 → 重新以此日為入住
    if (!checkIn || (checkIn && checkOut)) {
      onSelectRange(dateStr, "")
      return
    }
    // 已選入住、未選退房：點選日 <= 入住 → 改為新入住
    if (dateStr <= checkIn) {
      onSelectRange(dateStr, "")
      return
    }
    // 以此日為退房（exclusive）；若區間跨越不可訂日 → 改以此日為新入住
    if (rangeHasBlocked(checkIn, dateStr)) {
      onSelectRange(dateStr, "")
      return
    }
    onSelectRange(checkIn, dateStr)
  }

  const inRange = (dateStr: string): boolean => {
    if (!checkIn) return false
    if (checkOut) return dateStr >= checkIn && dateStr <= checkOut
    return dateStr === checkIn
  }

  const dim = daysInMonth(view.year, view.month)
  const firstWeekday = new Date(view.year, view.month, 1).getDay()
  const cells: (number | null)[] = []
  for (let i = 0; i < firstWeekday; i++) cells.push(null)
  for (let day = 1; day <= dim; day++) cells.push(day)

  const goPrev = () => {
    setLoading(true)
    setView((v) => (v.month === 0 ? { year: v.year - 1, month: 11 } : { year: v.year, month: v.month - 1 }))
  }
  const goNext = () => {
    setLoading(true)
    setView((v) => (v.month === 11 ? { year: v.year + 1, month: 0 } : { year: v.year, month: v.month + 1 }))
  }

  return (
    <div data-testid="listing-calendar" className="flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <button
          type="button"
          data-testid="calendar-prev"
          onClick={goPrev}
          aria-label="上個月"
          className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-rs-hairline text-rs-ink transition-colors hover:border-rs-primary"
        >
          ‹
        </button>
        <span className="text-sm font-medium text-rs-ink">
          {view.year} 年 {view.month + 1} 月
        </span>
        <button
          type="button"
          data-testid="calendar-next"
          onClick={goNext}
          aria-label="下個月"
          className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-rs-hairline text-rs-ink transition-colors hover:border-rs-primary"
        >
          ›
        </button>
      </div>

      {error && <p className="text-sm text-rs-error">{error}</p>}

      <div className="grid grid-cols-7 gap-1 text-center text-xs text-rs-ink-muted">
        {WEEKDAYS.map((w) => (
          <div key={w}>{w}</div>
        ))}
      </div>

      <div className="grid grid-cols-7 gap-1" aria-busy={loading}>
        {cells.map((day, idx) => {
          if (day === null) return <div key={"empty-" + idx} />
          const dateStr = iso(view.year, view.month, day)
          const unavailable = isUnavailable(dateStr)
          const selected = inRange(dateStr)
          return (
            <button
              key={dateStr}
              type="button"
              data-testid={`calendar-day-${dateStr}`}
              data-unavailable={unavailable ? "true" : "false"}
              disabled={unavailable}
              onClick={() => handleDayClick(dateStr)}
              className={[
                "flex min-h-[3rem] flex-col items-center justify-center gap-0.5 rounded-md py-1 text-sm transition-colors",
                unavailable
                  ? "cursor-not-allowed text-rs-ink-muted line-through opacity-40"
                  : "text-rs-ink hover:border-rs-primary",
                selected ? "bg-rs-primary text-white" : "border border-rs-hairline",
              ].join(" ")}
            >
              <span>{day}</span>
              {!unavailable && (
                <span className="flex flex-col items-center leading-none">
                  {originalPriceByDate[dateStr] != null && (
                    <span
                      data-testid={`calendar-original-price-${dateStr}`}
                      className={
                        selected
                          ? "text-[9px] text-white/70 line-through"
                          : "text-[9px] text-rs-ink-muted line-through"
                      }
                    >
                      {formatCellPrice(currency, originalPriceByDate[dateStr])}
                    </span>
                  )}
                  <span
                    data-testid={`calendar-price-${dateStr}`}
                    className={selected ? "text-[10px] text-white/90" : "text-[10px] text-rs-ink-muted"}
                  >
                    {formatCellPrice(currency, priceByDate[dateStr] ?? basePrice)}
                  </span>
                </span>
              )}
            </button>
          )
        })}
      </div>

      <p className="text-xs text-rs-ink-muted">灰色刪除線為不可預訂日；點選日期挑選入住與退房。</p>
    </div>
  )
}
