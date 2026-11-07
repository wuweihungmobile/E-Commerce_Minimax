import type { Metadata } from 'next'
import SupplierForm from '@/components/erp/SupplierForm'
import { PageTitle } from '@/components/ui/page-title'

export const metadata: Metadata = {
  title: '供應商詳情',
}

export default async function ViewSupplierPage({ params }: { params: Promise<{ id: string }> }) {
  const { id: supplierId } = await params

  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <PageTitle>供應商詳情</PageTitle>
          </div>
          <SupplierForm supplierId={supplierId} mode="view" />
        </div>
      </main>
    </div>
  )
}