'use client'

import { useParams } from 'next/navigation'
import PurchaseOrderForm from '@/components/erp/PurchaseOrderForm'
import { PageTitle } from '@/components/ui/page-title'

export default function ViewPurchaseOrderPage() {
  const params = useParams()

  const orderId = params.id as string

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