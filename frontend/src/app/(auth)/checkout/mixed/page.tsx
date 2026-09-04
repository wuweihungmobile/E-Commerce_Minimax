'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'
import OrderPaymentService from '@/services/payment'
import AddressService, { type Address } from '@/services/address'
import checkoutService, { checkoutErrorMessage, type MixedCheckoutRequest } from '@/services/checkout'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Skeleton } from '@/components/ui/skeleton'
import { StorefrontShell } from '@/components/layout/StorefrontShell'

interface CartItem {
  cartItemKey: string
  listingId: string
  listingName: string
  quantity: number
  unitPrice: number
  subtotal: number
  listingType: string
  startDate?: string
  endDate?: string
}

const PHONE_PATTERN = /^09\d{8}$/

function formatPrice(price: number) {
  return new Intl.NumberFormat('zh-TW', { style: 'currency', currency: 'TWD' }).format(price)
}

function formatDate(dateStr: string | undefined) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-TW', { year: 'numeric', month: 'numeric', day: 'numeric' })
}

/**
 * 合併結帳頁（Sprint 127，DEF-048 擴大範圍）：購物車同時有 PRODUCT 與 ROOM 項目時，
 * 一次動作同時建立 Order（PRODUCT）與 Booking（ROOM），單一張優惠券的折扣分攤到兩側
 * （POST /v2/checkout/mixed，見 CombinedCheckoutService）。
 *
 * <p>表單整合既有兩頁的必要片段：收件地址（比照 checkout/product/page.tsx）＋旅客資料
 * （比照 checkout/page.tsx）＋一個共用的促銷碼輸入框。送出成功後沿用 checkout/product/page.tsx
 * 既有的付款流程——ROOM 側本來就沒有另外的付款步驟，訂單付款完成即整筆結帳完成。
 */
export default function MixedCheckoutPage() {
  const router = useRouter()
  const [loadingCart, setLoadingCart] = useState(true)
  const [productItems, setProductItems] = useState<CartItem[]>([])
  const [roomItem, setRoomItem] = useState<CartItem | null>(null)
  const [addresses, setAddresses] = useState<Address[]>([])
  const [selectedAddressId, setSelectedAddressId] = useState<string | null>(null)
  const [useManualAddress, setUseManualAddress] = useState(false)
  const [manualRecipient, setManualRecipient] = useState('')
  const [manualPhone, setManualPhone] = useState('')
  const [manualAddress, setManualAddress] = useState('')

  const [guestName, setGuestName] = useState('')
  const [guestPhone, setGuestPhone] = useState('')
  const [guestEmail, setGuestEmail] = useState('')
  const [specialRequests, setSpecialRequests] = useState('')
  const [promoCode, setPromoCode] = useState('')

  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoadingCart(true)
      setError(null)
      try {
        const [cartResponse, addressList] = await Promise.all([
          apiClient.get<{ data: { items: CartItem[] } }>(API_ENDPOINTS.cart.get),
          AddressService.list().catch(() => [] as Address[]),
        ])
        if (cancelled) return
        const items = cartResponse.data.data.items || []
        setProductItems(items.filter((i) => i.listingType === 'PRODUCT'))
        const rooms = items.filter((i) => i.listingType === 'ROOM')
        setRoomItem(rooms[0] || null)
        setAddresses(addressList)
        const defaultAddress = addressList.find((a) => a.isDefault) || addressList[0]
        if (defaultAddress) {
          setSelectedAddressId(defaultAddress.id)
        } else {
          setUseManualAddress(true)
        }
      } catch {
        if (cancelled) return
        setError('無法載入購物車，請稍後再試')
      } finally {
        if (!cancelled) setLoadingCart(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [])

  const productSubtotal = productItems.reduce((sum, item) => sum + item.subtotal, 0)
  const roomSubtotal = roomItem?.subtotal || 0

  function validate(): string | null {
    if (productItems.length === 0 || !roomItem) {
      return '購物車須同時有商品與房型項目才能合併結帳'
    }
    if (useManualAddress || addresses.length === 0) {
      if (!manualRecipient.trim()) return '請填寫收件人姓名'
      if (!PHONE_PATTERN.test(manualPhone.trim())) return '電話格式錯誤（需為 09 開頭 10 位數字）'
      if (!manualAddress.trim()) return '請填寫收件地址'
    } else if (!selectedAddressId) {
      return '請選擇收件地址'
    }
    if (!guestName.trim()) return '請填寫入住旅客姓名'
    if (guestPhone.trim() && !PHONE_PATTERN.test(guestPhone.trim().replace(/\s/g, ''))) {
      return '入住旅客電話格式不正確（需為 09 開頭的 10 位數字）'
    }
    return null
  }

  async function handleSubmit() {
    const validationError = validate()
    if (validationError) {
      setFormError(validationError)
      return
    }
    setFormError(null)
    setSubmitting(true)
    try {
      const request: MixedCheckoutRequest = {
        ...(useManualAddress || addresses.length === 0
          ? {
              shippingRecipientName: manualRecipient.trim(),
              shippingPhone: manualPhone.trim(),
              shippingAddress: manualAddress.trim(),
            }
          : { addressId: selectedAddressId || undefined }),
        guestCount: roomItem?.quantity || 1,
        guestName: guestName.trim(),
        guestPhone: guestPhone.trim() || undefined,
        guestEmail: guestEmail.trim() || undefined,
        specialRequests: specialRequests.trim() || undefined,
        promoCode: promoCode.trim() || undefined,
      }
      const idempotencyKey = crypto.randomUUID()
      const result = await checkoutService.checkoutMixed(request, idempotencyKey)

      const paymentState = await OrderPaymentService.getPaymentState(result.order.id)
      if (paymentState.paymentProvider === 'stripe') {
        const session = await OrderPaymentService.createCheckoutSession(result.order.id)
        window.location.href = session.sessionUrl
        return
      }
      await OrderPaymentService.pay(result.order.id)
      router.push(`/orders/${result.order.id}`)
    } catch (err: unknown) {
      const code = (err as { response?: { data?: { code?: string } } })?.response?.data?.code
      setFormError(checkoutErrorMessage(code))
      setSubmitting(false)
    }
  }

  if (loadingCart) {
    return (
      <StorefrontShell>
        <Skeleton className="h-8 w-48 mb-6" />
        <div className="space-y-4">
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-32 w-full" />
        </div>
      </StorefrontShell>
    )
  }

  return (
    <StorefrontShell>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">合併結帳</h1>
        <p className="mt-1 text-sm text-gray-600">一次動作同時結清商品與訂房，優惠券折扣將分攤至兩者</p>
      </div>

      {error && (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {!error && (productItems.length === 0 || !roomItem) ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16">
            <div className="text-5xl mb-4">🛒</div>
            <h2 className="text-lg font-semibold text-gray-900 mb-1">購物車須同時有商品與房型項目</h2>
            <p className="text-sm text-gray-500">請先返回購物車確認項目</p>
          </CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-6">
            <Card>
              <CardHeader>
                <CardTitle>收件地址（商品配送用）</CardTitle>
                <CardDescription>選擇已存地址，或手動輸入</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                {addresses.length > 0 && !useManualAddress && (
                  <div className="space-y-2">
                    {addresses.map((address) => (
                      <label
                        key={address.id}
                        className="flex items-start gap-3 rounded-md border p-3 cursor-pointer hover:bg-gray-50"
                      >
                        <input
                          type="radio"
                          name="address"
                          className="mt-1"
                          checked={selectedAddressId === address.id}
                          onChange={() => setSelectedAddressId(address.id)}
                        />
                        <div className="text-sm">
                          <div className="font-medium text-gray-900">
                            {address.recipientName} {address.phone}
                          </div>
                          <div className="text-gray-600">
                            {address.postalCode ? address.postalCode + ' ' : ''}
                            {address.city}
                            {address.district || ''}
                            {address.addressLine}
                          </div>
                        </div>
                      </label>
                    ))}
                    <Button variant="outline" size="sm" onClick={() => setUseManualAddress(true)}>
                      改用其他地址
                    </Button>
                  </div>
                )}

                {(useManualAddress || addresses.length === 0) && (
                  <div className="space-y-3">
                    {addresses.length > 0 && (
                      <Button variant="outline" size="sm" onClick={() => setUseManualAddress(false)}>
                        改選已存地址
                      </Button>
                    )}
                    <div className="space-y-2">
                      <Label htmlFor="manualRecipient">收件人姓名 *</Label>
                      <Input
                        id="manualRecipient"
                        value={manualRecipient}
                        onChange={(e) => setManualRecipient(e.target.value)}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="manualPhone">電話 *</Label>
                      <Input
                        id="manualPhone"
                        placeholder="0912345678"
                        value={manualPhone}
                        onChange={(e) => setManualPhone(e.target.value)}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="manualAddress">收件地址 *</Label>
                      <Input
                        id="manualAddress"
                        value={manualAddress}
                        onChange={(e) => setManualAddress(e.target.value)}
                      />
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>入住旅客資料</CardTitle>
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
                </div>
                <div className="space-y-2">
                  <Label htmlFor="guestPhone">電話（選填）</Label>
                  <Input
                    id="guestPhone"
                    placeholder="0912345678"
                    value={guestPhone}
                    onChange={(e) => setGuestPhone(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="guestEmail">Email（選填）</Label>
                  <Input
                    id="guestEmail"
                    type="email"
                    placeholder="example@email.com"
                    value={guestEmail}
                    onChange={(e) => setGuestEmail(e.target.value)}
                  />
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

            <Card>
              <CardHeader>
                <CardTitle>訂單明細</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div>
                  <div className="text-sm font-medium text-gray-700 mb-2">商品</div>
                  <div className="space-y-2">
                    {productItems.map((item) => (
                      <div key={item.cartItemKey} className="flex justify-between text-sm">
                        <span>
                          {item.listingName} x {item.quantity}
                        </span>
                        <span>{formatPrice(item.subtotal)}</span>
                      </div>
                    ))}
                  </div>
                </div>
                {roomItem && (
                  <div className="border-t pt-3">
                    <div className="text-sm font-medium text-gray-700 mb-2">房型</div>
                    <div className="flex justify-between text-sm">
                      <span>
                        {roomItem.listingName}
                        {roomItem.startDate && roomItem.endDate && (
                          <span className="text-gray-500">
                            {' '}
                            （{formatDate(roomItem.startDate)} - {formatDate(roomItem.endDate)}）
                          </span>
                        )}
                      </span>
                      <span>{formatPrice(roomItem.subtotal)}</span>
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>

          <div>
            <Card>
              <CardHeader>
                <CardTitle>結帳摘要</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {formError && (
                  <Alert variant="destructive">
                    <AlertDescription>{formError}</AlertDescription>
                  </Alert>
                )}
                <div className="flex justify-between text-sm">
                  <span className="text-gray-600">商品小計</span>
                  <span>{formatPrice(productSubtotal)}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-gray-600">房型小計</span>
                  <span>{formatPrice(roomSubtotal)}</span>
                </div>
                <div className="text-xs text-gray-500">運費將於建立訂單時計算，優惠券折扣將分攤至兩者</div>

                <div className="space-y-2">
                  <Label htmlFor="promoCode">優惠碼（選填）</Label>
                  <Input
                    id="promoCode"
                    placeholder="輸入優惠碼"
                    value={promoCode}
                    onChange={(e) => setPromoCode(e.target.value)}
                    disabled={submitting}
                  />
                </div>

                <div className="border-t pt-4">
                  <Button className="w-full" size="lg" onClick={handleSubmit} disabled={submitting}>
                    {submitting ? '處理中...' : '確認送出並付款'}
                  </Button>
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      )}
    </StorefrontShell>
  )
}
