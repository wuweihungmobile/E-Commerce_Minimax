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
}

export default new ReviewService()
