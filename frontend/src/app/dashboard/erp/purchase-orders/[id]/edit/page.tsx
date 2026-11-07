import type { Metadata } from 'next'
import PurchaseOrderForm from '@/components/erp/PurchaseOrderForm'
import { PageTitle } from '@/components/ui/page-title'

export const metadata: Metadata = {
  title: '編輯採購訂單',
}

export default async function EditPurchaseOrderPage({ params }: { params: Promise<{ id: string }> }) {
  const { id: orderId } = await params

  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <PageTitle>編輯採購訂單</PageTitle>
          </div>
          <PurchaseOrderForm orderId={orderId} mode="edit" />
        </div>
      </main>
    </div>
  )
}