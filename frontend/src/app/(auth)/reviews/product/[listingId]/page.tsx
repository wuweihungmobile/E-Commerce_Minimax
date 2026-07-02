'use client'

import { useParams } from 'next/navigation'
import ReviewList from '@/components/reviews/ReviewList'
import { StorefrontShell } from '@/components/layout/StorefrontShell'

export default function ProductReviewsPage() {
  const params = useParams()
  const listingId = params.listingId as string

  return (
    <StorefrontShell>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">商品評價</h1>
      <ReviewList listingId={listingId} />
    </StorefrontShell>
  )
}
