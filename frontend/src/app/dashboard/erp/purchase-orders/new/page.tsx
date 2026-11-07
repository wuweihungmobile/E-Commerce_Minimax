import type { Metadata } from 'next'
import PurchaseOrderForm from '@/components/erp/PurchaseOrderForm'
import { PageTitle } from '@/components/ui/page-title'

export const metadata: Metadata = {
  title: '新增採購訂單',
}

export default function NewPurchaseOrderPage() {
  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <PageTitle>新增採購訂單</PageTitle>
          </div>
          <PurchaseOrderForm mode="create" />
        </div>
      </main>
    </div>
  )
}