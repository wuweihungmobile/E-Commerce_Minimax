'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import TenantDetail from '@/components/tenant/TenantDetail'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { LoadingState } from '@/components/ui/states'

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
            <LoadingState />
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
            <PageTitle>店鋪詳情</PageTitle>
            <PageDescription>
              檢視店鋪詳細資訊
            </PageDescription>
          </div>
          <TenantDetail tenantId={tenantId} />
        </div>
      </main>
    </div>
  )
}
