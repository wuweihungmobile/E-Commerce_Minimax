'use client'

import TenantList from '@/components/tenant/TenantList'
import { PageTitle, PageDescription } from '@/components/ui/page-title'

export default function TenantsPage() {
  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <PageTitle>店鋪管理</PageTitle>
            <PageDescription>
              管理您的店鋪資訊，切換不同店鋪
            </PageDescription>
          </div>
          <TenantList />
        </div>
      </main>
    </div>
  )
}
