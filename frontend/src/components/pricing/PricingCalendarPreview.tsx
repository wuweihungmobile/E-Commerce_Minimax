'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import PricingService, { CalculatePriceRequest, CalculatePriceResponse, PriceBreakdown } from '@/services/pricing'
import AuthService from '@/services/auth'

interface PricingCalendarProps {
  roomListingId: string
}

export default function PricingCalendarPreview({ roomListingId }: PricingCalendarProps) {
  const router = useRouter()
  const [checkInDate, setCheckInDate] = useState('')
  const [checkOutDate, setCheckOutDate] = useState('')
  const [guestCount, setGuestCount] = useState(1)
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<CalculatePriceResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
    }
  }, [router])

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