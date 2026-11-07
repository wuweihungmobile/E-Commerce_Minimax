import type { Metadata } from 'next'
import SupplierForm from '@/components/erp/SupplierForm'
import { PageTitle } from '@/components/ui/page-title'

export const metadata: Metadata = {
  title: '編輯供應商',
}

export default async function EditSupplierPage({ params }: { params: Promise<{ id: string }> }) {
  const { id: supplierId } = await params

  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <PageTitle>編輯供應商</PageTitle>
          </div>
          <SupplierForm supplierId={supplierId} mode="edit" />
        </div>
      </main>
    </div>
  )
}