'use client'

import { use } from 'react'
import TenantDetail from '@/components/tenant/TenantDetail'
import { PageTitle, PageDescription } from '@/components/ui/page-title'

interface TenantDetailPageProps {
  params: Promise<{
    id: string
  }>
}

export default function TenantDetailPage({ params }: TenantDetailPageProps) {
  const { id: tenantId } = use(params)

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
