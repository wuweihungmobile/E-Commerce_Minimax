'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import RoomList from '@/components/room/RoomList'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'
import { PageTitle, PageDescription } from '@/components/ui/page-title'

export default function RoomsPage() {
  const router = useRouter()

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
            <PageTitle>房源管理</PageTitle>
            <PageDescription>
              管理您的房源資料，上下架房源
            </PageDescription>
          </div>
          <RoomList />
        </div>
      </main>
    </div>
  )
}