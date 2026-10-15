'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Input } from '@/components/ui/input'
import PricingCalendarPreview from '@/components/pricing/PricingCalendarPreview'
import AuthService from '@/services/auth'
import { useCurrentUserEmail } from '@/hooks/useCurrentUserEmail'

// DEF-321(e)：PRD §10.1.2 列為獨立路由 /dashboard/pricing/calendar，
// 但原實作只把 <PricingCalendarPreview /> 嵌在 /dashboard/pricing/rules 篩選房源 ID 之後，
// 沒有獨立頁面可用。本頁重用同一元件，提供對齊 PRD 路由表的獨立入口。
export default function PricingCalendarPage() {
  const router = useRouter()
  const userEmail = useCurrentUserEmail()
  const [roomListingId, setRoomListingId] = useState('')

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
  }, [router])

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center gap-4">
              <Link href="/dashboard" className="text-gray-600 hover:text-gray-900">
                Dashboard
              </Link>
              <span className="text-gray-400">/</span>
              <Link href="/dashboard/pricing/rules" className="text-gray-600 hover:text-gray-900">
                定價規則
              </Link>
              <span className="text-gray-400">/</span>
              <span className="text-gray-900 font-medium">定價日曆預覽</span>
            </div>
            <div className="flex items-center gap-4">
              <span className="text-sm text-gray-600">
                {userEmail}
              </span>
              <button
                onClick={async () => {
                  await AuthService.logout()
                  router.push('/login')
                }}
                className="px-3 py-1.5 text-sm text-white bg-red-500 rounded-md hover:bg-red-600"
              >
                登出
              </button>
            </div>
          </div>
        </div>
      </nav>

      <main className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="px-4 py-6 sm:px-0">
          <div className="mb-6">
            <h1 className="text-2xl font-bold text-gray-900">定價日曆預覽</h1>
            <p className="mt-1 text-sm text-gray-600">
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
