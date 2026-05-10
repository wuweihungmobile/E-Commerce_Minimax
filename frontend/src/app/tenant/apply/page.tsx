'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import TenantApplyForm from '@/components/tenant/TenantApplyForm'
import AuthService from '@/services/auth'

export default function TenantApplyPage() {
  const router = useRouter()

  useEffect(() => {
    // 檢查用戶是否已登入
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
            <div className="flex items-center">
              <h1 className="text-xl font-bold text-gray-900">NextKey</h1>
            </div>
          </div>
        </div>
      </nav>

      <main className="py-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="mb-8">
            <h2 className="text-2xl font-bold text-gray-900">申請開店</h2>
            <p className="mt-1 text-sm text-gray-600">
              填寫店鋪資訊以開始使用平台服務
            </p>
          </div>
          <TenantApplyForm />
        </div>
      </main>
    </div>
  )
}
