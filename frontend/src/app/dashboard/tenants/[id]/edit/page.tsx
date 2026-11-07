import type { Metadata } from 'next'
import TenantEditForm from '@/components/tenant/TenantEditForm'
import { PageTitle, PageDescription } from '@/components/ui/page-title'

export const metadata: Metadata = {
  title: '編輯店鋪',
}

interface TenantEditPageProps {
  params: Promise<{
    id: string
  }>
}

export default async function TenantEditPage({ params }: TenantEditPageProps) {
  const { id: tenantId } = await params

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
