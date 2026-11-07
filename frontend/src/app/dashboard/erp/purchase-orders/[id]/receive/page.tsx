import type { Metadata } from 'next'
import PurchaseOrderForm from '@/components/erp/PurchaseOrderForm'
import { PageTitle } from '@/components/ui/page-title'

export const metadata: Metadata = {
  title: '收貨確認',
}

export default async function ReceivePurchaseOrderPage({ params }: { params: Promise<{ id: string }> }) {
  const { id: orderId } = await params

  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <PageTitle>收貨確認</PageTitle>
          </div>
          <PurchaseOrderForm orderId={orderId} mode="receive" />
        </div>
      </main>
    </div>
  )
}