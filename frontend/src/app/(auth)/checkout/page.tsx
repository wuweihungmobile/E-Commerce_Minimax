'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { StorefrontShell } from '@/components/layout/StorefrontShell'

interface BookingItem {
  listingId: string
  listingName: string
  coverImageUrl: string | null
  skuId?: string
  skuCode?: string
  specName?: string
  quantity: number
  unitPrice: number
  subtotal: number
  listingType: string
  startDate?: string
  endDate?: string
}

interface CheckoutData {
  items: BookingItem[]
  totalAmount: number
  appliedPromoCode?: string
  discountAmount?: number
  finalAmount?: number
}

interface CreateBookingRequest {
  roomListingId: string
  checkInDate: string
  checkOutDate: string
  guestCount: number
  guestName: string
  guestPhone?: string
  guestEmail?: string
  specialRequests?: string
}

interface BookingResponse {
  id: string
  tenantId: string
  userId: string
  roomListingId: string
  roomTitle: string
  checkInDate: string
  checkOutDate: string
  guestCount: number
  status: string
  totalAmount: number
  currency: string
  nightsCount: number
  createdAt: string
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

export default function CheckoutPage() {
  const router = useRouter()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [bookingId, setBookingId] = useState<string | null>(null)

  // Guest info form state
  const [guestName, setGuestName] = useState('')
  const [guestPhone, setGuestPhone] = useState('')
  const [guestEmail, setGuestEmail] = useState('')
  const [specialRequests, setSpecialRequests] = useState('')

  // Form validation
  const [validationErrors, setValidationErrors] = useState<Record<string, string>>({})

  const generateUUIDv4 = (): string => {
    return crypto.randomUUID()
  }

  const formatPrice = (price: number) => {
    return new Intl.NumberFormat('zh-TW', { style: 'currency', currency: 'TWD' }).format(price)
  }

  const formatDate = (dateStr: string | undefined) => {
    if (!dateStr) return ''
    const date = new Date(dateStr)
    return date.toLocaleDateString('zh-TW', { year: 'numeric', month: 'numeric', day: 'numeric' })
  }

  const validateForm = (): boolean => {
    const errors: Record<string, string> = {}

    if (!guestName.trim()) {
      errors.guestName = '請填寫姓名'
    }

    if (!guestPhone.trim()) {
      errors.guestPhone = '請填寫電話'
    } else if (!/^09\d{8}$/.test(guestPhone.replace(/\s/g, ''))) {
      errors.guestPhone = '電話格式不正確（需為 09 開頭的 10 位數字）'
    }

    if (!guestEmail.trim()) {
      errors.guestEmail = '請填寫 Email'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(guestEmail)) {
      errors.guestEmail = 'Email 格式不正確'
    }

    setValidationErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleCreateBooking = async () => {
    if (!validateForm()) return

    setLoading(true)
    setError(null)

    try {
      // Get cart data first
      const cartResponse = await apiClient.get<{ data: { items: BookingItem[]; totalAmount: number } }>(
        API_ENDPOINTS.cart.get
      )
      const cartData = cartResponse.data.data

      if (!cartData.items || cartData.items.length === 0) {
        setError('購物車是空的，請先選擇商品')
        setLoading(false)
        return
      }

      // For room bookings, create a booking for each ROOM item
      const roomItems = cartData.items.filter(item => item.listingType === 'ROOM')

      if (roomItems.length === 0) {
        setError('沒有可預訂的房間')
        setLoading(false)
        return
      }

      // Create booking for the first ROOM item (assuming one at a time for now)
      const roomItem = roomItems[0]
      const idempotencyKey = generateUUIDv4()

      const request: CreateBookingRequest = {
        roomListingId: roomItem.listingId,
        checkInDate: roomItem.startDate || '',
        checkOutDate: roomItem.endDate || '',
        guestCount: roomItem.quantity,
        guestName: guestName.trim(),
        guestPhone: guestPhone.trim(),
        guestEmail: guestEmail.trim(),
        specialRequests: specialRequests.trim() || undefined
      }

      const response = await apiClient.post<ApiResponse<BookingResponse>>(
        API_ENDPOINTS.bookings.create,
        request,
        {
          headers: {
            'Idempotency-Key': idempotencyKey
          }
        }
      )

      if (response.data.success && response.data.data) {
        setBookingId(response.data.data.id)
        // Clear cart after successful booking
        await apiClient.delete(API_ENDPOINTS.cart.clear)
      }
    } catch (err: unknown) {
      console.error('Booking failed:', err)
      const errorResponse = err as { response?: { data?: { code?: string; message?: string } } }
      if (errorResponse?.response?.data?.code === 'E_6005') {
        setError('預訂正在處理中，請稍候...')
      } else if (errorResponse?.response?.data?.code === 'E-4001') {
        setError('抱歉，此日期範圍已不可用，請返回選擇其他日期')
      } else if (errorResponse?.response?.data?.code === 'E-9004') {
        setError('請求格式錯誤，請重新嘗試')
      } else {
        setError('預訂失敗，請稍後再試')
      }
    } finally {
      setLoading(false)
    }
  }

  // Show success screen
  if (bookingId) {
    return (
      <StorefrontShell>
        <Card className="max-w-xl mx-auto">
          <CardContent className="flex flex-col items-center justify-center py-12">
            <div className="text-6xl mb-4">✓</div>
            <h2 className="text-2xl font-bold text-gray-900 mb-2">預訂成功！</h2>
            <p className="text-gray-600 mb-4">您的預訂已完成</p>
            <p className="text-sm text-gray-500 mb-6">預訂編號: {bookingId}</p>
            <div className="flex gap-4">
              <Link href="/bookings">
                <Button variant="outline">查看我的預訂</Button>
              </Link>
              <Link href="/">
                <Button>返回首頁</Button>
              </Link>
            </div>
          </CardContent>
        </Card>
      </StorefrontShell>
    )
  }

  return (
    <StorefrontShell>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">填寫預訂資料</h1>
        <p className="mt-1 text-sm text-gray-600">
          請填寫以下資料以完成預訂
        </p>
      </div>

      {error && (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Guest Info Form */}
        <div className="lg:col-span-2">
          <Card>
            <CardHeader>
              <CardTitle>旅客資料</CardTitle>
              <CardDescription>請填寫入住旅客的資料</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="guestName">姓名 *</Label>
                <Input
                  id="guestName"
                  placeholder="請輸入姓名"
                  value={guestName}
                  onChange={(e) => setGuestName(e.target.value)}
                />
                {validationErrors.guestName && (
                  <p className="text-sm text-red-500">{validationErrors.guestName}</p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="guestPhone">電話 *</Label>
                <Input
                  id="guestPhone"
                  placeholder="0912345678"
                  value={guestPhone}
                  onChange={(e) => setGuestPhone(e.target.value)}
                />
                {validationErrors.guestPhone && (
                  <p className="text-sm text-red-500">{validationErrors.guestPhone}</p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="guestEmail">Email *</Label>
                <Input
                  id="guestEmail"
                  type="email"
                  placeholder="example@email.com"
                  value={guestEmail}
                  onChange={(e) => setGuestEmail(e.target.value)}
                />
                {validationErrors.guestEmail && (
                  <p className="text-sm text-red-500">{validationErrors.guestEmail}</p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="specialRequests">特殊要求（選填）</Label>
                <Input
                  id="specialRequests"
                  placeholder="例如：需要嬰兒床、遲入住等"
                  value={specialRequests}
                  onChange={(e) => setSpecialRequests(e.target.value)}
                />
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Order Summary */}
        <div>
          <Card>
            <CardHeader>
              <CardTitle>訂單摘要</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="text-sm text-gray-600">
                請從購物車選擇要預訂的房間
              </div>

              <div className="border-t pt-4">
                <Button
                  className="w-full"
                  size="lg"
                  onClick={handleCreateBooking}
                  disabled={loading}
                >
                  {loading ? '處理中...' : '確認預訂'}
                </Button>
              </div>

              <div className="text-xs text-gray-500 text-center">
                點擊「確認預訂」即表示您同意我們的服務條款和隱私政策
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </StorefrontShell>
  )
}