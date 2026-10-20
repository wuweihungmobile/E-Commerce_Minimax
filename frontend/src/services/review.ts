import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'

// 對齊後端 ReviewDto / BookingReviewDto（M08 評價）

export type ReviewType = 'PRODUCT' | 'ROOM'

export interface CreateReviewRequest {
  listingId: string
  orderId?: string
  bookingId?: string
  rating: number // 1-5
  title?: string
  content: string
  images?: string[]
  isAnonymous?: boolean
}

export interface Review {
  reviewId: string
  listingId: string
  listingTitle: string | null
  userId: string | null
  userFullName: string
  userAvatarUrl: string | null
  orderId: string | null
  bookingId: string | null
  reviewType: ReviewType
  rating: number
  title: string | null
  content: string
  images: string[] | null
  helpfulCount: number
  isAnonymous: boolean
  // Sprint 287（DEF-392）：後端 ReviewDto.ReviewResponse 既有欄位，原前端介面缺漏
  isHandled: boolean
  handledAt: string | null
  handledBy: string | null
  createdAt: string
  updatedAt: string
}

export interface ReviewReply {
  replyId: string
  reviewId: string
  replierId: string
  content: string
  createdAt: string
  updatedAt: string
}

export interface ReviewListResult {
  reviews: Review[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  averageRating: number | null
  totalReviews: number | null
}

export interface RatingStats {
  listingId: string
  averageRating: number | null
  totalReviews: number
  rating1Count: number
  rating2Count: number
  rating3Count: number
  rating4Count: number
  rating5Count: number
}

export interface CreateBookingReviewRequest {
  bookingId: string
  rating: number
  title?: string
  content: string
  images?: string[]
  isAnonymous?: boolean
}

interface ApiResponse<T> {
  success: boolean
  code?: string
  message?: string
  data: T
}

class ReviewService {
  async createReview(request: CreateReviewRequest): Promise<Review> {
    const response = await apiClient.post<ApiResponse<Review>>(
      API_ENDPOINTS.reviews.create,
      request
    )
    return response.data.data
  }

  async getListingReviews(
    listingId: string,
    page = 0,
    size = 10,
    minRating?: number
  ): Promise<ReviewListResult> {
    const params = new URLSearchParams()
    params.append('page', String(page))
    params.append('size', String(size))
    if (minRating !== undefined) params.append('minRating', String(minRating))
    const response = await apiClient.get<ApiResponse<ReviewListResult>>(
      API_ENDPOINTS.reviews.listingReviews(listingId) + '?' + params.toString()
    )
    return response.data.data
  }

  async getRatingStats(listingId: string): Promise<RatingStats> {
    const response = await apiClient.get<ApiResponse<RatingStats>>(
      API_ENDPOINTS.reviews.listingStats(listingId)
    )
    return response.data.data
  }

  async createBookingReview(request: CreateBookingReviewRequest): Promise<void> {
    await apiClient.post(API_ENDPOINTS.bookingReviews.create, request)
  }

  // ========== 賣家回覆與評價管理（Sprint 287，DEF-392） ==========

  async getReplies(reviewId: string): Promise<ReviewReply[]> {
    const response = await apiClient.get<ApiResponse<{ replies: ReviewReply[]; totalCount: number }>>(
      API_ENDPOINTS.reviews.replies(reviewId)
    )
    return response.data.data.replies
  }

  async createReply(reviewId: string, content: string): Promise<ReviewReply> {
    const response = await apiClient.post<ApiResponse<ReviewReply>>(
      API_ENDPOINTS.reviews.replies(reviewId),
      { content }
    )
    return response.data.data
  }

  async setHandled(reviewId: string, handled: boolean): Promise<Review> {
    const response = await apiClient.put<ApiResponse<Review>>(
      API_ENDPOINTS.reviews.handle(reviewId) + '?handled=' + handled
    )
    return response.data.data
  }

  async getManagedReviews(isHandled: boolean, page = 0, size = 20): Promise<ReviewListResult> {
    const params = new URLSearchParams()
    params.append('isHandled', String(isHandled))
    params.append('page', String(page))
    params.append('size', String(size))
    const response = await apiClient.get<ApiResponse<ReviewListResult>>(
      API_ENDPOINTS.reviews.managed + '?' + params.toString()
    )
    return response.data.data
  }
}

export default new ReviewService()
