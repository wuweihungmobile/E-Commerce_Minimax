'use client'

import { useParams } from 'next/navigation'
import SupplierForm from '@/components/erp/SupplierForm'
import { PageTitle } from '@/components/ui/page-title'

export default function EditSupplierPage() {
  const params = useParams()

  const supplierId = params.id as string

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