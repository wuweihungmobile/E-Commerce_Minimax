import type { Metadata } from 'next'
import SupplierForm from '@/components/erp/SupplierForm'
import { PageTitle } from '@/components/ui/page-title'

export const metadata: Metadata = {
  title: '新增供應商',
}

export default function NewSupplierPage() {
  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <PageTitle>新增供應商</PageTitle>
          </div>
          <SupplierForm mode="create" />
        </div>
      </main>
    </div>
  )
}