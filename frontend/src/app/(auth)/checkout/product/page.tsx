'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'
import OrderService from '@/services/order'
import OrderPaymentService from '@/services/payment'
import AddressService, { type Address } from '@/services/address'
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
}

const PHONE_PATTERN = /^09\d{8}$/

const ERROR_MESSAGES: Record<string, string> = {
  'E-5004': '購物車無商品項目，請先加入商品',
  'E-3004': '部分商品庫存不足，請減少數量或稍後再試',
  'E-3002': '部分商品已下架，請重新確認購物車',
  'E-8007': '無權使用此收件地址',
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosErr = error as { response?: { data?: { code?: string; message?: string } } }
    const code = axiosErr.response?.data?.code
    if (code && ERROR_MESSAGES[code]) return ERROR_MESSAGES[code]
    return axiosErr.response?.data?.message || fallback
  }
  return fallback
}

function formatPrice(price: number) {
  return new Intl.NumberFormat('zh-TW', { style: 'currency', currency: 'TWD' }).format(price)
}

export default function ProductCheckoutPage() {
  const router = useRouter()
  const [loadingCart, setLoadingCart] = useState(true)
  const [items, setItems] = useState<CartItem[]>([])
  const [addresses, setAddresses] = useState<Address[]>([])
  const [selectedAddressId, setSelectedAddressId] = useState<string | null>(null)
  const [useManualAddress, setUseManualAddress] = useState(false)
  const [manualRecipient, setManualRecipient] = useState('')
  const [manualPhone, setManualPhone] = useState('')
  const [manualAddress, setManualAddress] = useState('')
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
        const productItems = (cartResponse.data.data.items || []).filter(
          (i) => i.listingType === 'PRODUCT'
        )
        setItems(productItems)
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

  const totalAmount = items.reduce((sum, item) => sum + item.subtotal, 0)

  function validate(): string | null {
    if (items.length === 0) return '購物車無商品項目'
    if (useManualAddress || addresses.length === 0) {
      if (!manualRecipient.trim()) return '請填寫收件人姓名'
      if (!PHONE_PATTERN.test(manualPhone.trim())) return '電話格式錯誤（需為 09 開頭 10 位數字）'
      if (!manualAddress.trim()) return '請填寫收件地址'
    } else if (!selectedAddressId) {
      return '請選擇收件地址'
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
      const order = await OrderService.createOrder(
        useManualAddress || addresses.length === 0
          ? {
              orderType: 'PRODUCT',
              shippingRecipientName: manualRecipient.trim(),
              shippingPhone: manualPhone.trim(),
              shippingAddress: manualAddress.trim(),
            }
          : { orderType: 'PRODUCT', addressId: selectedAddressId || undefined }
      )

      const paymentState = await OrderPaymentService.getPaymentState(order.id)
      if (paymentState.paymentProvider === 'stripe') {
        const session = await OrderPaymentService.createCheckoutSession(order.id)
        window.location.href = session.sessionUrl
        return
      }
      await OrderPaymentService.pay(order.id)
      router.push(`/orders/${order.id}`)
    } catch (err) {
      setFormError(extractErrorMessage(err, '建立訂單失敗，請稍後再試'))
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
        <h1 className="text-2xl font-bold text-gray-900">商品結帳</h1>
        <p className="mt-1 text-sm text-gray-600">請確認收件地址並完成付款</p>
      </div>

      {error && (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {!error && items.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16">
            <div className="text-5xl mb-4">🛒</div>
            <h2 className="text-lg font-semibold text-gray-900 mb-1">購物車無商品項目</h2>
            <p className="text-sm text-gray-500">請先至購物車加入商品</p>
          </CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-6">
            <Card>
              <CardHeader>
                <CardTitle>收件地址</CardTitle>
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
                <CardTitle>商品明細</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2">
                {items.map((item) => (
                  <div key={item.cartItemKey} className="flex justify-between text-sm">
                    <span>
                      {item.listingName} x {item.quantity}
                    </span>
                    <span>{formatPrice(item.subtotal)}</span>
                  </div>
                ))}
              </CardContent>
            </Card>
          </div>

          <div>
            <Card>
              <CardHeader>
                <CardTitle>訂單摘要</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {formError && (
                  <Alert variant="destructive">
                    <AlertDescription>{formError}</AlertDescription>
                  </Alert>
                )}
                <div className="flex justify-between text-sm">
                  <span className="text-gray-600">商品小計</span>
                  <span>{formatPrice(totalAmount)}</span>
                </div>
                <div className="text-xs text-gray-500">運費將於建立訂單時計算</div>
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
