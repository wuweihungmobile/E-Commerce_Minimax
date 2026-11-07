import type { Metadata } from 'next'
import TenantDetail from '@/components/tenant/TenantDetail'
import { PageTitle, PageDescription } from '@/components/ui/page-title'

export const metadata: Metadata = {
  title: '店鋪詳情',
}

interface TenantDetailPageProps {
  params: Promise<{
    id: string
  }>
}

export default async function TenantDetailPage({ params }: TenantDetailPageProps) {
  const { id: tenantId } = await params

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
