'use client'

import { useParams } from 'next/navigation'
import ReviewList from '@/components/reviews/ReviewList'
import { StorefrontShell } from '@/components/layout/StorefrontShell'
import { PageTitle } from '@/components/ui/page-title'

export default function ProductReviewsPage() {
  const params = useParams()
  const listingId = params.listingId as string

  return (
    <StorefrontShell>
      <PageTitle className="mb-6">商品評價</PageTitle>
      <ReviewList listingId={listingId} />
    </StorefrontShell>
  )
}
