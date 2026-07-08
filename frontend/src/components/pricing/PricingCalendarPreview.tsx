'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import PricingService, { CalculatePriceRequest, CalculatePriceResponse, PriceBreakdown } from '@/services/pricing'
import AuthService from '@/services/auth'
import type { CalendarDay } from '@/services/booking'

interface PricingCalendarProps {
  roomListingId: string
}

const CALENDAR_PREVIEW_DAYS = 90

export default function PricingCalendarPreview({ roomListingId }: PricingCalendarProps) {
  const router = useRouter()
  const [checkInDate, setCheckInDate] = useState('')
  const [checkOutDate, setCheckOutDate] = useState('')
  const [guestCount, setGuestCount] = useState(1)
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<CalculatePriceResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  // 未來 90 天定價日曆總覽（Sprint 83，PRD P0），掛載時自動載入，不需手動選日期。
  const [calendarDays, setCalendarDays] = useState<CalendarDay[]>([])
  const [calendarLoading, setCalendarLoading] = useState(true)
  const [calendarError, setCalendarError] = useState<string | null>(null)

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
    }
  }, [router])

  // 載入未來 90 天定價日曆（async；不在 effect 內同步 setState，遵循 React 19 嚴格 hooks，比照 MonthCalendar 既有模式）
  useEffect(() => {
    let cancelled = false
    const formatDateParam = (date: Date) => date.toISOString().split('T')[0]
    const today = new Date()
    const start = formatDateParam(today)
    const end = formatDateParam(new Date(today.getTime() + (CALENDAR_PREVIEW_DAYS - 1) * 24 * 60 * 60 * 1000))

    PricingService.getCalendarPreview(roomListingId, start, end)
      .then((days) => {
        if (cancelled) return
        setCalendarDays(days)
        setCalendarError(null)
      })
      .catch(() => {
        if (!cancelled) setCalendarError('載入定價日曆失敗')
      })
      .finally(() => {
        if (!cancelled) setCalendarLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [roomListingId])

  const getDefaultDates = () => {
    const today = new Date()
    const tomorrow = new Date(today)
    tomorrow.setDate(tomorrow.getDate() + 1)

    const formatDate = (date: Date) => date.toISOString().split('T')[0]
    return {
      checkIn: formatDate(tomorrow),
      checkOut: formatDate(new Date(tomorrow.getTime() + 2 * 24 * 60 * 60 * 1000)),
    }
  }

  useEffect(() => {
    const defaults = getDefaultDates()
    setCheckInDate(defaults.checkIn)
    setCheckOutDate(defaults.checkOut)
  }, [])

  const handleCalculate = async () => {
    if (!checkInDate || !checkOutDate) {
      setError('請選擇入住和退房日期')
      return
    }

    setLoading(true)
    setError(null)

    try {
      const request: CalculatePriceRequest = {
        roomListingId,
        checkInDate,
        checkOutDate,
        guestCount,
      }
      const response = await PricingService.calculatePrice(request)
      setResult(response)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '計算價格失敗')
      } else {
        setError('計算價格失敗')
      }
    } finally {
      setLoading(false)
    }
  }

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleDateString('zh-TW', {
      month: 'short',
      day: 'numeric',
      weekday: 'short',
    })
  }

  const formatPrice = (price: number, currency: string = 'TWD') => {
    return new Intl.NumberFormat('zh-TW', {
      style: 'currency',
      currency,
      minimumFractionDigits: 0,
    }).format(price)
  }

  const isWeekend = (dateStr: string) => {
    const day = new Date(dateStr).getDay()
    return day === 0 || day === 6
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>定價日曆預覽</CardTitle>
        <CardDescription>查看未來 90 天內的價格計算</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <div className="space-y-2">
          <h3 className="text-sm font-medium">未來 {CALENDAR_PREVIEW_DAYS} 天日曆總覽</h3>
          {calendarLoading && (
            <div className="text-sm text-muted-foreground">載入中...</div>
          )}
          {calendarError && (
            <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md text-sm">
              {calendarError}
            </div>
          )}
          {!calendarLoading && !calendarError && (
            <div className="border rounded-lg max-h-80 overflow-y-auto divide-y">
              {calendarDays.map((day) => (
                <div key={day.date} className="grid grid-cols-4 gap-4 p-2 items-center text-sm">
                  <div>
                    <div>{formatDate(day.date)}</div>
                    <div className={`text-xs ${isWeekend(day.date) ? 'text-blue-600' : 'text-gray-500'}`}>
                      {isWeekend(day.date) ? '週末' : '平日'}
                    </div>
                  </div>
                  <div className="text-muted-foreground">
                    {day.status === 'NOT_OPEN' ? '未開放' : day.status === 'AVAILABLE' ? '可訂' : day.status === 'BOOKED' ? '已訂' : day.status}
                  </div>
                  <div className="text-right">
                    {day.originalPrice != null && (
                      <span className="text-xs text-gray-400 line-through mr-1">
                        {formatPrice(day.originalPrice)}
                      </span>
                    )}
                    {day.price != null && <span>{formatPrice(day.price)}</span>}
                  </div>
                  <div className="text-right">
                    {day.appliedRuleName && (
                      <Badge variant="outline" className="text-xs">
                        {day.appliedRuleName}
                      </Badge>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="border-t pt-4">
          <h3 className="text-sm font-medium mb-3">試算特定入住區間</h3>
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">
            {error}
          </div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div>
            <label className="text-sm font-medium">入住日期</label>
            <Input
              type="date"
              value={checkInDate}
              onChange={(e) => setCheckInDate(e.target.value)}
              min={new Date().toISOString().split('T')[0]}
              max={new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]}
              className="mt-1"
            />
          </div>
          <div>
            <label className="text-sm font-medium">退房日期</label>
            <Input
              type="date"
              value={checkOutDate}
              onChange={(e) => setCheckOutDate(e.target.value)}
              min={checkInDate}
              max={new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]}
              className="mt-1"
            />
          </div>
          <div>
            <label className="text-sm font-medium">入住人數</label>
            <Input
              type="number"
              min="1"
              max="20"
              value={guestCount}
              onChange={(e) => setGuestCount(parseInt(e.target.value) || 1)}
              className="mt-1"
            />
          </div>
          <div className="flex items-end">
            <Button onClick={handleCalculate} disabled={loading} className="w-full">
              {loading ? '計算中...' : '計算價格'}
            </Button>
          </div>
        </div>

        {result && (
          <div className="space-y-4">
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 p-4 bg-gray-50 rounded-lg">
              <div>
                <div className="text-sm text-muted-foreground">晚數</div>
                <div className="text-2xl font-bold">{result.nights}</div>
              </div>
              <div>
                <div className="text-sm text-muted-foreground">總金額</div>
                <div className="text-2xl font-bold text-primary">
                  {formatPrice(result.adjustedTotal, result.currency)}
                </div>
              </div>
              <div>
                <div className="text-sm text-muted-foreground">基礎價格</div>
                <div className="text-lg">
                  {formatPrice(result.baseTotal, result.currency)}
                </div>
              </div>
              <div>
                <div className="text-sm text-muted-foreground">折扣</div>
                <div className="text-lg text-green-600">
                  {result.discount > 0 ? '-' : ''}{formatPrice(result.discount, result.currency)}
                </div>
              </div>
            </div>

            <div className="border rounded-lg overflow-hidden">
              <div className="grid grid-cols-1 divide-y">
                {result.breakdown.map((day: PriceBreakdown, index: number) => (
                  <div key={index} className="grid grid-cols-4 gap-4 p-3 items-center">
                    <div className="text-sm">
                      <div>{formatDate(day.date)}</div>
                      <div className={`text-xs ${isWeekend(day.date) ? 'text-blue-600' : 'text-gray-500'}`}>
                        {isWeekend(day.date) ? '週末' : '平日'}
                      </div>
                    </div>
                    <div className="text-right">
                      <div className="text-sm text-muted-foreground">基礎</div>
                      <div className="text-sm">{formatPrice(day.basePrice, result.currency)}</div>
                    </div>
                    <div className="text-right">
                      <div className="text-sm text-muted-foreground">調整後</div>
                      <div className="text-sm font-medium">{formatPrice(day.adjustedPrice, result.currency)}</div>
                    </div>
                    <div className="text-right">
                      {day.appliedRuleName && (
                        <Badge variant="outline" className="text-xs">
                          {day.appliedRuleName}
                        </Badge>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function Badge({ variant, className, children }: { variant?: string; className?: string; children: React.ReactNode }) {
  const variantClasses: Record<string, string> = {
    outline: 'border bg-transparent px-2 py-0.5 text-xs rounded',
    default: 'bg-primary px-2 py-0.5 text-xs rounded text-primary-foreground',
  }
  return <span className={`${variantClasses[variant || 'default']} ${className || ''}`}>{children}</span>
}