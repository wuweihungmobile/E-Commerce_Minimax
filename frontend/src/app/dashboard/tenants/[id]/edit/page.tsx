'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import TenantEditForm from '@/components/tenant/TenantEditForm'
import AuthService from '@/services/auth'

interface TenantEditPageProps {
  params: Promise<{
    id: string
  }>
}

export default function TenantEditPage({ params }: TenantEditPageProps) {
  const router = useRouter()
  const [tenantId, setTenantId] = useState<string | null>(null)
  const [user, setUser] = useState<{ email: string; role: string; tenantId: string | null } | null>(null)

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }

    const currentUser = AuthService.getCurrentUser()
    // Use setTimeout to avoid synchronous setState in effect
    setTimeout(() => {
      setUser(currentUser)
    }, 0)

    // Unwrap params
    params.then((resolvedParams) => {
      setTenantId(resolvedParams.id)
    })
  }, [router, params])

  // Render loading state until we have the tenant ID
  if (!tenantId || !user) {
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

  // StoreOwner role cannot edit tenants - redirect to 403 or tenant list
  if (user.role === 'StoreOwner') {
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
            <div className="flex flex-col items-center justify-center h-64">
              <h2 className="text-2xl font-bold text-gray-900 mb-4">無權限訪問</h2>
              <p className="text-gray-600 mb-6">
                您的角色 (StoreOwner) 沒有編輯店鋪的權限。
              </p>
              <button
                onClick={() => router.push('/dashboard/tenants')}
                className="px-4 py-2 bg-primary text-white rounded-md hover:bg-primary/90"
              >
                返回店鋪列表
              </button>
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
              <button
                onClick={() => router.push(`/dashboard/tenants/${tenantId}`)}
                className="text-gray-600 hover:text-gray-900"
              >
                店鋪詳情
              </button>
              <span className="text-gray-400">/</span>
              <span className="text-gray-900 font-medium">編輯</span>
            </div>
            <div className="flex items-center gap-4">
              <span className="text-sm text-gray-600">
                {user.email}
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
            <h1 className="text-2xl font-bold text-gray-900">編輯店鋪</h1>
            <p className="mt-1 text-sm text-gray-600">
              修改店鋪的基本資訊
            </p>
          </div>
          <TenantEditForm tenantId={tenantId} />
        </div>
      </main>
    </div>
  )
}
