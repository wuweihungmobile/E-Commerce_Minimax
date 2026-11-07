import type { Metadata } from 'next'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { PageTitle } from '@/components/ui/page-title'
import { StorefrontShell } from '@/components/layout/StorefrontShell'

export const metadata: Metadata = {
  title: '訂單付款已取消',
}

/**
 * Stripe Checkout 取消頁（真實金流 Phase A，AI-2410）。
 * 買家於 Stripe 付款頁取消時回跳；訂單維持未付款，可重新付款。
 */
export default async function PaymentCancelPage({ params }: { params: Promise<{ id: string }> }) {
  const { id: orderId } = await params

  return (
    <StorefrontShell>
      <div className="mx-auto max-w-2xl px-4 py-8">
        <Card data-testid="payment-cancel-card">
          <CardHeader>
            <PageTitle className="text-base leading-none tracking-tight">付款已取消</PageTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-rs-ink-muted">
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
