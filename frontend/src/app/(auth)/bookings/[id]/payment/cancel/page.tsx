'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { StorefrontShell } from '@/components/layout/StorefrontShell'

/**
 * 訂房 Stripe Checkout 取消頁（Sprint 222，DEF-303 (1)）。
 * 買家於 Stripe 付款頁取消時回跳；訂房維持待付款，可回到預訂重新付款。
 */
export default function BookingPaymentCancelPage() {
  const params = useParams()
  const bookingId = params.id as string

  return (
    <StorefrontShell>
      <div className="mx-auto max-w-2xl px-4 py-8">
        <Card data-testid="booking-payment-cancel-card">
          <CardHeader>
            <CardTitle>付款已取消</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-gray-600">您已取消本次付款，預訂尚未付款。您可以返回預訂重新付款。</p>
            <div className="flex gap-3">
              <Link href={`/bookings/${bookingId}`}>
                <Button data-testid="booking-payment-cancel-back">返回預訂</Button>
              </Link>
            </div>
          </CardContent>
        </Card>
      </div>
    </StorefrontShell>
  )
}
