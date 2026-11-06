'use client'

import { useParams } from 'next/navigation'
import PurchaseOrderForm from '@/components/erp/PurchaseOrderForm'
import { PageTitle } from '@/components/ui/page-title'

export default function ReceivePurchaseOrderPage() {
  const params = useParams()

  const orderId = params.id as string

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