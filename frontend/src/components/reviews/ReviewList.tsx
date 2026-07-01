'use client'

import { useState, useEffect } from 'react'
import ReviewService, { type Review, type RatingStats } from '@/services/review'
import ReviewStars from './ReviewStars'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

interface ReviewListProps {
  listingId: string
  // 用於在提交新評價後觸發重新載入
  reloadKey?: number
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('zh-TW', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

export default function ReviewList({ listingId, reloadKey = 0 }: ReviewListProps) {
  const [reviews, setReviews] = useState<Review[]>([])
  const [stats, setStats] = useState<RatingStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      try {
        const [list, ratingStats] = await Promise.all([
          ReviewService.getListingReviews(listingId, 0, 20),
          ReviewService.getRatingStats(listingId).catch(() => null),
        ])
        if (cancelled) return
        setReviews(list.reviews)
        setStats(ratingStats)
      } catch {
        if (cancelled) return
        setError('無法載入評價')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [listingId, reloadKey])

  if (loading) {
    return (
      <div className="space-y-3">
        {[0, 1].map((i) => (
          <Card key={i}>
            <CardContent className="py-4">
              <Skeleton className="h-4 w-24 mb-2" />
              <Skeleton className="h-4 w-full" />
            </CardContent>
          </Card>
        ))}
      </div>
    )
  }

  if (error) {
    return <p className="text-sm text-red-500">{error}</p>
  }

  return (
    <div className="space-y-4">
      {/* 統計摘要 */}
      {stats && stats.totalReviews > 0 ? (
        <div className="flex items-center gap-3">
          <span className="text-2xl font-bold text-gray-900">
            {stats.averageRating != null ? stats.averageRating.toFixed(1) : '—'}
          </span>
          <ReviewStars rating={Math.round(stats.averageRating ?? 0)} size="sm" />
          <span className="text-sm text-gray-500">（{stats.totalReviews} 則評價）</span>
        </div>
      ) : (
        <p className="text-sm text-gray-500">尚無評價</p>
      )}

      {/* 評價列表 */}
      {reviews.map((review) => (
        <Card key={review.reviewId}>
          <CardContent className="py-4">
            <div className="flex items-center justify-between gap-2 mb-1">
              <div className="flex items-center gap-2">
                <ReviewStars rating={review.rating} size="sm" />
                <span className="text-sm font-medium text-gray-900">
                  {review.isAnonymous ? '匿名' : review.userFullName}
                </span>
              </div>
              <span className="text-xs text-gray-400">{formatDate(review.createdAt)}</span>
            </div>
            {review.title && <p className="text-sm font-medium text-gray-900">{review.title}</p>}
            <p className="text-sm text-gray-700 whitespace-pre-line">{review.content}</p>
            {review.helpfulCount > 0 && (
              <p className="text-xs text-gray-400 mt-1">{review.helpfulCount} 人覺得有幫助</p>
            )}
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
