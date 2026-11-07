import type { Metadata } from 'next'
import PurchaseOrderForm from '@/components/erp/PurchaseOrderForm'
import { PageTitle } from '@/components/ui/page-title'

export const metadata: Metadata = {
  title: '採購訂單詳情',
}

export default async function ViewPurchaseOrderPage({ params }: { params: Promise<{ id: string }> }) {
  const { id: orderId } = await params

  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <PageTitle>採購訂單詳情</PageTitle>
          </div>
          <PurchaseOrderForm orderId={orderId} mode="view" />
        </div>
      </main>
    </div>
  )
}