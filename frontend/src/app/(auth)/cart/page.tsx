'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import AuthService from '@/services/auth'
import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { StorefrontShell } from '@/components/layout/StorefrontShell'

interface CartItem {
  cartItemKey: string
  listingId: string
  listingName: string
  coverImageUrl: string | null
  quantity: number
  unitPrice: number
  subtotal: number
  listingType?: string
  startDate: string | null
  endDate: string | null
  // 動態定價調整（AI-2403 折扣 / AI-2406c 漲價）：規則生效時填入（unitPrice/subtotal 已為調整後）。
  // discountAmount 為有號差額（正=折扣、負=加價）；priceAdjustmentType 明示方向。
  originalUnitPrice?: number | null
  discountAmount?: number | null
  appliedRuleName?: string | null
  priceAdjustmentType?: string | null
}

interface CartResponse {
  cartId: string
  userId: string
  items: CartItem[]
  totalAmount: number
  itemCount: number
  // Sprint 101：後端一律回傳預估運費（PRODUCT 項目小計為基數），並讓 finalAmount 含運費
  shippingFee?: number
  appliedPromoCode?: string
  discountAmount?: number
  finalAmount?: number
}

export default function CartPage() {
  const router = useRouter()
  const [cart, setCart] = useState<CartResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [updating, setUpdating] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [promoCode, setPromoCode] = useState('')
  const [promoLoading, setPromoLoading] = useState(false)
  const [promoError, setPromoError] = useState<string | null>(null)
  const [promoSuccess, setPromoSuccess] = useState<string | null>(null)

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
    fetchCart()
  }, [router])

  const fetchCart = async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await apiClient.get<{ data: CartResponse }>(API_ENDPOINTS.cart.get)
      setCart(response.data.data)
    } catch (err: unknown) {
      console.error('Failed to fetch cart:', err)
      setError('載入購物車失敗，請稍後再試')
    } finally {
      setLoading(false)
    }
  }

  const updateQuantity = async (cartItemKey: string, newQuantity: number) => {
    if (newQuantity < 0 || newQuantity > 999) return

    // If quantity is 0, remove the item
    if (newQuantity === 0) {
      await removeItem(cartItemKey)
      return
    }

    setUpdating(cartItemKey)
    setError(null)

    try {
      const response = await apiClient.put<{ data: { cartItemKey: string; quantity: number; subtotal: number } }>(
        API_ENDPOINTS.cart.update(cartItemKey),
        { quantity: newQuantity }
      )

      // Update local state
      setCart(prev => {
        if (!prev) return prev
        return {
          ...prev,
          items: prev.items.map(item =>
            item.cartItemKey === cartItemKey
              ? { ...item, quantity: response.data.data.quantity, subtotal: response.data.data.subtotal }
              : item
          ),
          totalAmount: prev.items.reduce((sum, item) => {
            if (item.cartItemKey === cartItemKey) {
              return sum + response.data.data.subtotal
            }
            return sum + item.subtotal
          }, 0)
        }
      })
    } catch (err: unknown) {
      console.error('Failed to update quantity:', err)
      setError('更新數量失敗，請稍後再試')
    } finally {
      setUpdating(null)
    }
  }

  const removeItem = async (cartItemKey: string) => {
    setUpdating(cartItemKey)
    setError(null)

    try {
      await apiClient.delete(API_ENDPOINTS.cart.remove(cartItemKey))

      // Update local state
      setCart(prev => {
        if (!prev) return prev
        const remainingItems = prev.items.filter(item => item.cartItemKey !== cartItemKey)
        return {
          ...prev,
          items: remainingItems,
          totalAmount: remainingItems.reduce((sum, item) => sum + item.subtotal, 0),
          itemCount: remainingItems.length
        }
      })
    } catch (err: unknown) {
      console.error('Failed to remove item:', err)
      setError('移除商品失敗，請稍後再試')
    } finally {
      setUpdating(null)
    }
  }

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return null
    const date = new Date(dateStr)
    return date.toLocaleDateString('zh-TW', { year: 'numeric', month: 'numeric', day: 'numeric' })
  }

  const formatPrice = (price: number) => {
    return new Intl.NumberFormat('zh-TW', { style: 'currency', currency: 'TWD' }).format(price)
  }

  const handleApplyPromo = async () => {
    if (!promoCode.trim() || !cart) return

    setPromoLoading(true)
    setPromoError(null)
    setPromoSuccess(null)

    try {
      // First validate
      const validateResponse = await apiClient.get<{ data: { valid: boolean; invalidReason?: string } }>(
        `${API_ENDPOINTS.cart.validatePromo}?code=${encodeURIComponent(promoCode)}`
      )

      const validation = validateResponse.data.data
      if (!validation.valid) {
        setPromoError(validation.invalidReason || '優惠券無效')
        setPromoLoading(false)
        return
      }

      // Then apply
      const applyResponse = await apiClient.post<{ data: { appliedPromoCode: string; shippingFee: number; discountAmount: number; finalAmount: number } }>(
        API_ENDPOINTS.cart.applyPromo,
        { promoCode }
      )

      const result = applyResponse.data.data
      setCart(prev => prev ? {
        ...prev,
        appliedPromoCode: result.appliedPromoCode,
        shippingFee: result.shippingFee,
        discountAmount: result.discountAmount,
        finalAmount: result.finalAmount,
      } : prev)

      setPromoSuccess(`已套用優惠券，折扣 ${formatPrice(result.discountAmount)}`)
      setPromoCode('')
    } catch (err: unknown) {
      console.error('Failed to apply promo:', err)
      setPromoError('優惠券套用失敗，請稍後再試')
    } finally {
      setPromoLoading(false)
    }
  }

  const handleRemovePromo = async () => {
    if (!cart) return

    setPromoLoading(true)
    setPromoError(null)

    try {
      await apiClient.delete(API_ENDPOINTS.cart.removePromo)

      setCart(prev => prev ? {
        ...prev,
        appliedPromoCode: undefined,
        discountAmount: 0,
        // 移除券後仍要收運費，不可直接清空 finalAmount 讓畫面退回「不含運費」的小計
        finalAmount: prev.totalAmount + (prev.shippingFee ?? 0),
      } : prev)

      setPromoSuccess(null)
    } catch (err: unknown) {
      console.error('Failed to remove promo:', err)
      setPromoError('移除優惠券失敗，請稍後再試')
    } finally {
      setPromoLoading(false)
    }
  }

  if (loading) {
    return (
      <StorefrontShell>
        <Skeleton className="h-8 w-48 mb-6" />
            <div className="space-y-4">
              {[1, 2, 3].map(i => (
                <Card key={i}>
                  <CardContent className="p-6">
                    <div className="flex gap-4">
                      <Skeleton className="w-24 h-24" />
                      <div className="flex-1 space-y-2">
                        <Skeleton className="h-6 w-48" />
                        <Skeleton className="h-4 w-24" />
                      </div>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
      </StorefrontShell>
    )
  }

  return (
    <StorefrontShell>
          <div className="mb-6">
            <h1 className="text-2xl font-bold text-gray-900">購物車</h1>
            <p className="mt-1 text-sm text-gray-600">
              檢視和管理您的購物車商品
            </p>
          </div>

          {error && (
            <Alert variant="destructive" className="mb-4">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          {!cart || cart.items.length === 0 ? (
            <Card>
              <CardContent className="flex flex-col items-center justify-center py-12">
                <div className="text-6xl mb-4">🛒</div>
                <p className="text-gray-500 mb-4">購物車是空的</p>
                <Link href="/">
                  <Button variant="outline">開始購物</Button>
                </Link>
              </CardContent>
            </Card>
          ) : (
            <div className="space-y-4">
              {/* Cart Items */}
              <div className="space-y-4">
                {cart.items.map(item => (
                  <Card key={item.cartItemKey}>
                    <CardContent className="p-6">
                      <div className="flex gap-4">
                        {/* Product Image */}
                        <div className="w-24 h-24 bg-gray-200 rounded-md flex-shrink-0 overflow-hidden">
                          {item.coverImageUrl ? (
                            <img
                              src={item.coverImageUrl}
                              alt={item.listingName}
                              className="w-full h-full object-cover"
                            />
                          ) : (
                            <div className="w-full h-full flex items-center justify-center text-gray-400">
                              無圖片
                            </div>
                          )}
                        </div>

                        {/* Product Info */}
                        <div className="flex-1">
                          <div className="flex justify-between">
                            <div>
                              <h3 className="text-lg font-medium text-gray-900">
                                {item.listingName}
                              </h3>
                              <p className="text-sm text-gray-500 mt-1">
                                {/* 動態定價調整（AI-2406c）：折扣（>0）原價刪除線；加價（<0）原價不刪除線 */}
                                {item.discountAmount != null &&
                                  item.discountAmount !== 0 &&
                                  item.originalUnitPrice != null && (
                                    <span
                                      data-testid={`cart-original-price-${item.cartItemKey}`}
                                      className={
                                        item.discountAmount > 0
                                          ? 'mr-1 text-gray-400 line-through'
                                          : 'mr-1 text-gray-400'
                                      }
                                    >
                                      {formatPrice(item.originalUnitPrice)}
                                    </span>
                                  )}
                                單價: {formatPrice(item.unitPrice)}
                              </p>
                              {item.appliedRuleName &&
                                item.discountAmount != null &&
                                item.discountAmount !== 0 && (
                                  <span
                                    data-testid={`cart-adjust-badge-${item.cartItemKey}`}
                                    className={
                                      item.discountAmount > 0
                                        ? 'inline-block mt-1 rounded bg-green-100 px-2 py-0.5 text-xs text-green-700'
                                        : 'inline-block mt-1 rounded bg-orange-100 px-2 py-0.5 text-xs text-orange-700'
                                    }
                                  >
                                    {item.appliedRuleName}｜{item.discountAmount > 0 ? '省 ' : '加價 '}
                                    {formatPrice(Math.abs(item.discountAmount))}
                                  </span>
                                )}
                              {item.startDate && item.endDate && (
                                <p className="text-sm text-gray-500">
                                  日期: {formatDate(item.startDate)} - {formatDate(item.endDate)}
                                </p>
                              )}
                            </div>
                            <div className="text-right">
                              <p className="text-lg font-medium text-gray-900">
                                {formatPrice(item.subtotal)}
                              </p>
                            </div>
                          </div>

                          {/* Quantity Controls */}
                          <div className="flex items-center justify-between mt-4">
                            <div className="flex items-center gap-2">
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => updateQuantity(item.cartItemKey, item.quantity - 1)}
                                disabled={updating === item.cartItemKey}
                              >
                                -
                              </Button>
                              <Input
                                type="number"
                                min="1"
                                max="999"
                                value={item.quantity}
                                onChange={(e) => {
                                  const val = parseInt(e.target.value)
                                  if (!isNaN(val)) {
                                    updateQuantity(item.cartItemKey, val)
                                  }
                                }}
                                className="w-16 text-center"
                                disabled={updating === item.cartItemKey}
                              />
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => updateQuantity(item.cartItemKey, item.quantity + 1)}
                                disabled={updating === item.cartItemKey || item.quantity >= 999}
                              >
                                +
                              </Button>
                            </div>
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => removeItem(item.cartItemKey)}
                              disabled={updating === item.cartItemKey}
                              className="text-red-500 hover:text-red-700 hover:bg-red-50"
                            >
                              移除
                            </Button>
                          </div>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                ))}
              </div>

              {/* Cart Summary */}
              <Card>
                <CardHeader>
                  <CardTitle>訂單摘要</CardTitle>
                  <CardDescription>
                    共 {cart.itemCount} 項商品
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="space-y-2">
                    <div className="flex justify-between">
                      <span className="text-gray-600">小計</span>
                      <span>{formatPrice(cart.totalAmount)}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-gray-600">運費</span>
                      <span>
                        {(cart.shippingFee ?? 0) > 0
                          ? formatPrice(cart.shippingFee ?? 0)
                          : '免運'}
                      </span>
                    </div>
                    {cart.appliedPromoCode && (
                      <div className="flex justify-between text-green-600">
                        <span>優惠折抵</span>
                        <span>-{formatPrice(cart.discountAmount || 0)}</span>
                      </div>
                    )}
                    <div className="flex justify-between text-lg font-medium border-t pt-2">
                      <span>總金額</span>
                      <span className="text-2xl">
                        {formatPrice(cart.finalAmount ?? cart.totalAmount + (cart.shippingFee ?? 0))}
                      </span>
                    </div>
                  </div>

                  {/* Promo Code Section */}
                  <div className="border-t pt-4">
                    {!cart.appliedPromoCode ? (
                      <div className="flex gap-2">
                        <Input
                          placeholder="輸入優惠券代碼"
                          value={promoCode}
                          onChange={(e) => setPromoCode(e.target.value)}
                          disabled={promoLoading}
                        />
                        <Button
                          variant="outline"
                          onClick={handleApplyPromo}
                          disabled={promoLoading || !promoCode.trim()}
                        >
                          {promoLoading ? '驗證中...' : '套用'}
                        </Button>
                      </div>
                    ) : (
                      <div className="flex items-center justify-between">
                        <Badge variant="success" className="text-sm">
                          已套用: {cart.appliedPromoCode}
                        </Badge>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={handleRemovePromo}
                          disabled={promoLoading}
                          className="text-red-500 hover:text-red-700"
                        >
                          移除
                        </Button>
                      </div>
                    )}
                    {promoError && (
                      <p className="text-sm text-red-500 mt-1">{promoError}</p>
                    )}
                    {promoSuccess && (
                      <p className="text-sm text-green-600 mt-1">{promoSuccess}</p>
                    )}
                  </div>

                  <Button
                    className="w-full"
                    size="lg"
                    onClick={() => {
                      // Sprint 127（DEF-048 擴大範圍）：購物車同時有 PRODUCT 與 ROOM 項目時，
                      // 導向合併結帳頁一次結清兩者；純單一類型仍走原有兩條各自獨立的結帳頁
                      // （該頁僅處理對應類型項目，另一類項目會保留在購物車）。
                      const hasProduct = (cart?.items || []).some((i) => i.listingType === 'PRODUCT')
                      const hasRoom = (cart?.items || []).some((i) => i.listingType === 'ROOM')
                      router.push(
                        hasProduct && hasRoom
                          ? '/checkout/mixed'
                          : hasProduct
                            ? '/checkout/product'
                            : '/checkout'
                      )
                    }}
                  >
                    前往結帳
                  </Button>
                </CardContent>
              </Card>

              {/* Continue Shopping */}
              <div className="text-center">
                <Link href="/">
                  <Button variant="link" className="text-gray-600">
                    繼續購物
                  </Button>
                </Link>
              </div>
            </div>
          )}
    </StorefrontShell>
  )
}