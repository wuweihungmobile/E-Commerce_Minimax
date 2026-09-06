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
import bookingService, { type CreateBookingRequest, bookingErrorMessage } from '@/services/booking'

interface BookingItem {
  cartItemKey: string
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

export default function CheckoutPage() {
  const router = useRouter()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [bookingId, setBookingId] = useState<string | null>(null)
  // Sprint 124（DEF-047／PRD US-010）：訂房結帳套用促銷碼。訂房沒有像 PRODUCT 購物車那樣
  // 預先驗證/套用的兩段式流程，改為送出預訂時一併帶入，由後端一次驗證與套用。
  const [promoCode, setPromoCode] = useState('')
  const [appliedPromoCode, setAppliedPromoCode] = useState<string | null>(null)
  const [discountAmount, setDiscountAmount] = useState<number>(0)

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
        guestPhone: guestPhone.trim().replace(/\s/g, ''),
        guestEmail: guestEmail.trim(),
        specialRequests: specialRequests.trim() || undefined,
        promoCode: promoCode.trim() || undefined
      }

      const booking = await bookingService.createBooking(request, idempotencyKey)
      setBookingId(booking.id)
      setAppliedPromoCode(booking.promoCode)
      setDiscountAmount(booking.discountAmount || 0)
      // 🔴 只移除「這次真的訂掉」的那一個 ROOM 項目，不可清空整車（DEF-043）。
      // 本流程只會為 roomItems[0] 建立預訂，若在此下 DELETE /v2/cart，購物車裡
      // 尚未結帳的 PRODUCT 項目、以及第二個以後的 ROOM 項目都會被靜默刪除。
      // 比照 PRODUCT 側既有作法（OrderService.createOrderFromCart 只 removeItem
      // 已處理項目，AI-2422）與 cart/page.tsx 的移除慣例。
      await apiClient.delete(API_ENDPOINTS.cart.remove(roomItem.cartItemKey))
    } catch (err: unknown) {
      console.error('Booking failed:', err)
      // 後端 ErrorCode wire code 為連字號（如 E-4001 日期衝突）；統一以 bookingErrorMessage 對應可讀訊息
      const code = (err as { response?: { data?: { code?: string } } })?.response?.data?.code
      setError(bookingErrorMessage(code))
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
            <p className="text-sm text-gray-500 mb-2">預訂編號: {bookingId}</p>
            {appliedPromoCode && (
              <p className="text-sm text-green-600 mb-4">
                已套用優惠券 {appliedPromoCode}，折扣 {formatPrice(discountAmount)}
              </p>
            )}
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

              <div className="space-y-2">
                <Label htmlFor="promoCode">優惠碼（選填）</Label>
                <Input
                  id="promoCode"
                  placeholder="輸入優惠碼"
                  value={promoCode}
                  onChange={(e) => setPromoCode(e.target.value)}
                  disabled={loading}
                />
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