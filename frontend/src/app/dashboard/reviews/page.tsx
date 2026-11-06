'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Pagination } from '@/components/ui/pagination'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import ReviewService, { type Review, type ReviewReply } from '@/services/review'
import ReviewStars from '@/components/reviews/ReviewStars'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'
import { Textarea } from '@/components/ui/textarea'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { EmptyState, LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'
import { toast } from '@/components/ui/toast'
import { apiErrorMessage, getApiErrorInfo } from '@/lib/api-error'

const PAGE_SIZE = 20

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('zh-TW', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

export default function DashboardReviewsPage() {
  const router = useRouter()

  const [reviews, setReviews] = useState<Review[]>([])
  const [replies, setReplies] = useState<Record<string, ReviewReply | null>>({})
  const [replyDrafts, setReplyDrafts] = useState<Record<string, string>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [forbidden, setForbidden] = useState(false)
  const [actionLoading, setActionLoading] = useState<string | null>(null)
  // 後端 /v2/reviews/managed 的 isHandled 為必填布林（無「全部」選項），預設顯示待處理項目
  const [isHandled, setIsHandled] = useState(false)
  const [currentPage, setCurrentPage] = useState(1)
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)

  const fetchReviews = useCallback(async (page: number, handled: boolean) => {
    setLoading(true)
    setError(null)
    setForbidden(false)
    try {
      const result = await ReviewService.getManagedReviews(handled, page - 1, PAGE_SIZE)
      setReviews(result.reviews)
      setTotalPages(result.totalPages || 1)
      setTotalElements(result.totalElements || 0)

      const replyEntries = await Promise.all(
        result.reviews.map(async (review) => {
          const reviewReplies = await ReviewService.getReplies(review.reviewId).catch(() => [])
          return [review.reviewId, reviewReplies[0] ?? null] as const
        })
      )
      setReplies(Object.fromEntries(replyEntries))
    } catch (err: unknown) {
      if (getApiErrorInfo(err).status === 403) {
        setForbidden(true)
      } else {
        setError(apiErrorMessage(err, '載入評價列表失敗'))
      }
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push(loginHref())
      return
    }
    fetchReviews(currentPage, isHandled)
  }, [router, fetchReviews, currentPage, isHandled])

  const handleReply = async (reviewId: string) => {
    const content = (replyDrafts[reviewId] || '').trim()
    if (!content) return
    setActionLoading(reviewId)
    try {
      const reply = await ReviewService.createReply(reviewId, content)
      setReplies((prev) => ({ ...prev, [reviewId]: reply }))
      setReplyDrafts((prev) => ({ ...prev, [reviewId]: '' }))
    } catch (err: unknown) {
      toast.error(apiErrorMessage(err, '回覆失敗'))
    } finally {
      setActionLoading(null)
    }
  }

  const handleToggleHandled = async (review: Review) => {
    setActionLoading(review.reviewId)
    try {
      await ReviewService.setHandled(review.reviewId, !review.isHandled)
      // 切換後這筆通常不再符合目前的篩選條件，重新整理列表
      await fetchReviews(currentPage, isHandled)
    } catch (err: unknown) {
      toast.error(apiErrorMessage(err, '更新處理狀態失敗'))
    } finally {
      setActionLoading(null)
    }
  }

  return (
    <div>
      <main>
        <div className="space-y-6">
          <div className="flex items-center justify-between flex-wrap gap-3">
            <div>
              <PageTitle>評價管理</PageTitle>
              <PageDescription>
                {totalElements > 0
                  ? `共 ${totalElements} 筆${isHandled ? '已處理' : '未處理'}評價`
                  : '目前沒有符合篩選條件的評價'}
              </PageDescription>
            </div>
            <Select
              value={isHandled ? 'HANDLED' : 'UNHANDLED'}
              onValueChange={(value) => {
                setIsHandled(value === 'HANDLED')
                setCurrentPage(1)
              }}
            >
              <SelectTrigger className="w-32">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="UNHANDLED">未處理</SelectItem>
                <SelectItem value="HANDLED">已處理</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {forbidden ? (
            <Card>
              <CardContent className="py-8 text-center text-rs-ink-muted">
                您的角色沒有權限管理評價（僅限店主／房東／管理員）
              </CardContent>
            </Card>
          ) : error ? (
            <Alert variant="destructive">{error}</Alert>
          ) : loading ? (
            <LoadingState />
          ) : reviews.length === 0 ? (
            <Card>
              <EmptyState title={isHandled ? '尚無已處理的評價' : '目前沒有待處理的評價'} className="h-48" />
            </Card>
          ) : (
            <div className="space-y-3">
              {reviews.map((review) => {
                const reply = replies[review.reviewId]
                return (
                  <Card key={review.reviewId}>
                    <CardContent className="py-4 space-y-3">
                      <div className="flex items-center justify-between gap-2 flex-wrap">
                        <div className="flex items-center gap-2">
                          <ReviewStars rating={review.rating} size="sm" />
                          <span className="text-sm font-medium text-rs-ink">
                            {review.isAnonymous ? '匿名' : review.userFullName}
                          </span>
                          <Badge variant={review.isHandled ? 'success' : 'warning'}>
                            {review.isHandled ? '已處理' : '未處理'}
                          </Badge>
                        </div>
                        <span className="text-xs text-rs-disabled-text">{formatDate(review.createdAt)}</span>
                      </div>
                      {review.title && <p className="text-sm font-medium text-rs-ink">{review.title}</p>}
                      <p className="text-sm text-rs-ink whitespace-pre-line">{review.content}</p>

                      {reply ? (
                        <div className="rounded-md bg-rs-bg-base border border-rs-hairline px-3 py-2">
                          <p className="text-xs font-medium text-rs-ink-muted mb-1">
                            您的回覆（{formatDate(reply.createdAt)}）
                          </p>
                          <p className="text-sm text-rs-ink whitespace-pre-line">{reply.content}</p>
                        </div>
                      ) : (
                        <div className="space-y-2">
                          <Textarea
                            placeholder="回覆這則評價..."
                            value={replyDrafts[review.reviewId] || ''}
                            onChange={(e) =>
                              setReplyDrafts((prev) => ({ ...prev, [review.reviewId]: e.target.value }))
                            }
                          />
                          <div className="flex justify-end">
                            <Button
                              size="sm"
                              onClick={() => handleReply(review.reviewId)}
                              disabled={
                                actionLoading === review.reviewId || !(replyDrafts[review.reviewId] || '').trim()
                              }
                            >
                              送出回覆
                            </Button>
                          </div>
                        </div>
                      )}

                      <div className="flex justify-end">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => handleToggleHandled(review)}
                          disabled={actionLoading === review.reviewId}
                        >
                          {review.isHandled ? '標記為未處理' : '標記為已處理'}
                        </Button>
                      </div>
                    </CardContent>
                  </Card>
                )
              })}
            </div>
          )}

          {totalPages > 1 && <Pagination current={currentPage} total={totalPages} onChange={setCurrentPage} />}
        </div>
      </main>
    </div>
  )
}
