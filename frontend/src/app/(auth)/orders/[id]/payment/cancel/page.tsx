'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { StorefrontShell } from '@/components/layout/StorefrontShell'

/**
 * Stripe Checkout 取消頁（真實金流 Phase A，AI-2410）。
 * 買家於 Stripe 付款頁取消時回跳；訂單維持未付款，可重新付款。
 */
export default function PaymentCancelPage() {
  const params = useParams()
  const orderId = params.id as string

  return (
    <StorefrontShell>
      <div className="mx-auto max-w-2xl px-4 py-8">
        <Card data-testid="payment-cancel-card">
          <CardHeader>
            <CardTitle>付款已取消</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-gray-600">
              您已取消本次付款，訂單尚未付款。您可以返回訂單重新付款。
            </p>
            <div className="flex gap-3">
              <Link href={`/orders/${orderId}`}>
                <Button data-testid="payment-cancel-back">返回訂單</Button>
              </Link>
            </div>
          </CardContent>
        </Card>
      </div>
    </StorefrontShell>
  )
}
