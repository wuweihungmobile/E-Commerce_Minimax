import type { Metadata } from 'next'
import ReviewList from '@/components/reviews/ReviewList'
import { StorefrontShell } from '@/components/layout/StorefrontShell'
import { PageTitle } from '@/components/ui/page-title'

export const metadata: Metadata = {
  title: '商品評價',
}

export default async function ProductReviewsPage({ params }: { params: Promise<{ listingId: string }> }) {
  const { listingId } = await params

  return (
    <StorefrontShell>
      <PageTitle className="mb-6">商品評價</PageTitle>
      <ReviewList listingId={listingId} />
    </StorefrontShell>
  )
}
