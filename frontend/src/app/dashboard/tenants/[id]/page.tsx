'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import TenantDetail from '@/components/tenant/TenantDetail'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

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
      router.push(loginHref())
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
      <div>
        <main>
          <div>
            <div className="flex items-center justify-center h-64">
              <div className="text-rs-ink-muted">載入中...</div>
            </div>
          </div>
        </main>
      </div>
    )
  }

  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <h1 className="text-2xl font-semibold text-rs-ink">店鋪詳情</h1>
            <p className="mt-1 text-sm text-rs-ink-muted">
              檢視店鋪詳細資訊
            </p>
          </div>
          <TenantDetail tenantId={tenantId} />
        </div>
      </main>
    </div>
  )
}
