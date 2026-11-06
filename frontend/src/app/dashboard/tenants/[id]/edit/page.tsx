'use client'

import { use } from 'react'
import TenantEditForm from '@/components/tenant/TenantEditForm'
import { PageTitle, PageDescription } from '@/components/ui/page-title'

interface TenantEditPageProps {
  params: Promise<{
    id: string
  }>
}

export default function TenantEditPage({ params }: TenantEditPageProps) {
  const { id: tenantId } = use(params)

  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <PageTitle>編輯店鋪</PageTitle>
            <PageDescription>
              修改店鋪的基本資訊
            </PageDescription>
          </div>
          <TenantEditForm tenantId={tenantId} />
        </div>
      </main>
    </div>
  )
}
