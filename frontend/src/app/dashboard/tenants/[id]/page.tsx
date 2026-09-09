'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import TenantDetail from '@/components/tenant/TenantDetail'
import AuthService from '@/services/auth'

interface TenantDetailPageProps {
  params: Promise<{
    id: string
  }>
}

export default function TenantDetailPage({ params }: TenantDetailPageProps) {
  const router = useRouter()
  const [tenantId, setTenantId] = useState<string | null>(null)

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }

    // Unwrap params
    params.then((resolvedParams) => {
      setTenantId(resolvedParams.id)
    })
  }, [router, params])

  // Render loading state until we have the tenant ID
  if (!tenantId) {
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
        <main className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
          <div className="px-4 py-6 sm:px-0">
            <div className="flex items-center justify-center h-64">
              <div className="text-gray-500">載入中...</div>
            </div>
          </div>
        </main>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center gap-4">
              <button
                onClick={() => router.push('/dashboard/tenants')}
                className="text-gray-600 hover:text-gray-900"
              >
                店鋪管理
              </button>
              <span className="text-gray-400">/</span>
              <span className="text-gray-900 font-medium">店鋪詳情</span>
            </div>
            <div className="flex items-center gap-4">
              <span className="text-sm text-gray-600">
                {AuthService.getCurrentUser()?.email}
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
            <h1 className="text-2xl font-bold text-gray-900">店鋪詳情</h1>
            <p className="mt-1 text-sm text-gray-600">
              檢視店鋪詳細資訊
            </p>
          </div>
          <TenantDetail tenantId={tenantId} />
        </div>
      </main>
    </div>
  )
}
