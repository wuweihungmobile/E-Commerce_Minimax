'use client'

import Link from 'next/link'
import { useParams } from 'next/navigation'
import ReviewList from '@/components/reviews/ReviewList'

export default function ProductReviewsPage() {
  const params = useParams()
  const listingId = params.listingId as string

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16 items-center">
            <div className="flex items-center gap-4">
              <Link href="/" className="text-xl font-bold text-gray-900">
                NextKey
              </Link>
              <span className="text-gray-400">/</span>
              <span className="text-gray-900 font-medium">商品評價</span>
            </div>
          </div>
        </div>
      </nav>

      <main className="max-w-3xl mx-auto py-6 px-4 sm:px-6 lg:px-8">
        <h1 className="text-2xl font-bold text-gray-900 mb-6">商品評價</h1>
        <ReviewList listingId={listingId} />
      </main>
    </div>
  )
}
