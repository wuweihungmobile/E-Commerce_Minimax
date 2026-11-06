'use client'

import { useState } from 'react'
import { Input } from '@/components/ui/input'
import PricingCalendarPreview from '@/components/pricing/PricingCalendarPreview'
import { PageTitle, PageDescription } from '@/components/ui/page-title'

// DEF-321(e)：PRD §10.1.2 列為獨立路由 /dashboard/pricing/calendar，
// 但原實作只把 <PricingCalendarPreview /> 嵌在 /dashboard/pricing/rules 篩選房源 ID 之後，
// 沒有獨立頁面可用。本頁重用同一元件，提供對齊 PRD 路由表的獨立入口。
export default function PricingCalendarPage() {
  const [roomListingId, setRoomListingId] = useState('')

  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <PageTitle>定價日曆預覽</PageTitle>
            <PageDescription>
              輸入房源 ID，預覽套用動態定價規則後的每日價格與可用狀態
            </PageDescription>
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
