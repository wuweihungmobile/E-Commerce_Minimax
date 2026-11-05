'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { Input } from '@/components/ui/input'
import PricingCalendarPreview from '@/components/pricing/PricingCalendarPreview'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

// DEF-321(e)：PRD §10.1.2 列為獨立路由 /dashboard/pricing/calendar，
// 但原實作只把 <PricingCalendarPreview /> 嵌在 /dashboard/pricing/rules 篩選房源 ID 之後，
// 沒有獨立頁面可用。本頁重用同一元件，提供對齊 PRD 路由表的獨立入口。
export default function PricingCalendarPage() {
  const router = useRouter()
  const [roomListingId, setRoomListingId] = useState('')

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push(loginHref())
      return
    }
  }, [router])

  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <h1 className="text-2xl font-semibold text-rs-ink">定價日曆預覽</h1>
            <p className="mt-1 text-sm text-rs-ink-muted">
              輸入房源 ID，預覽套用動態定價規則後的每日價格與可用狀態
            </p>
          </div>

          <div className="mb-6 max-w-md">
            <Input
              type="search"
              placeholder="輸入房源 ID..."
              value={roomListingId}
              onChange={(e) => setRoomListingId(e.target.value)}
              data-testid="pricing-calendar-room-input"
            />
          </div>

          {roomListingId && (
            <div data-testid="pricing-calendar-preview">
              <PricingCalendarPreview roomListingId={roomListingId} />
            </div>
          )}
        </div>
      </main>
    </div>
  )
}
