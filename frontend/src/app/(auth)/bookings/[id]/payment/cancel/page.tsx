import type { Metadata } from 'next'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { PageTitle } from '@/components/ui/page-title'
import { StorefrontShell } from '@/components/layout/StorefrontShell'

export const metadata: Metadata = {
  title: '預訂付款已取消',
}

/**
 * 訂房 Stripe Checkout 取消頁（Sprint 222，DEF-303 (1)）。
 * 買家於 Stripe 付款頁取消時回跳；訂房維持待付款，可回到預訂重新付款。
 */
export default async function BookingPaymentCancelPage({ params }: { params: Promise<{ id: string }> }) {
  const { id: bookingId } = await params

  return (
    <StorefrontShell>
      <div className="mx-auto max-w-2xl px-4 py-8">
        <Card data-testid="booking-payment-cancel-card">
          <CardHeader>
            <PageTitle className="text-base leading-none tracking-tight">付款已取消</PageTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-rs-ink-muted">您已取消本次付款，預訂尚未付款。您可以返回預訂重新付款。</p>
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
