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
  // Sprint 237（DEF-319 同店結帳）：商品／房源所屬的店鋪；房源已不存在時為 null（該項目無法結帳）
  storeId?: string | null
  storeName?: string | null
  // Sprint 239：店鋪是否營業中（非 ACTIVE 的店鋪不能下單，結帳會回 E-2010）
  storeActive?: boolean | null
}

// Sprint 237：後端 CartDto.StoreCartSummary——每家店鋪各自的小計、運費（該店鋪的運費模板）、已套用的促銷碼與折扣
interface StoreSummary {
  storeId: string
  storeName?: string | null
  /** 店鋪是否營業中（Sprint 239）；false 時標示「暫停營業」並停用結帳。舊版回應沒有此欄位時視為營業中。 */
  storeActive?: boolean | null
  itemCount: number
  totalAmount: number
  shippingFee: number
  appliedPromoCode?: string | null
  discountAmount: number
  finalAmount: number
}

interface CartResponse {
  cartId: string
  userId: string
  items: CartItem[]
  totalAmount: number
  // Sprint 146：後端 CartDto.CartResponse 以 @JsonProperty("totalItems") 序列化此欄位，
  // 先前前端宣告為 itemCount，導致「共 __ 項商品」永遠讀到 undefined
  totalItems: number
  // Sprint 101：後端一律回傳預估運費（PRODUCT 項目小計為基數），並讓 finalAmount 含運費
  shippingFee?: number
  appliedPromoCode?: string
  discountAmount?: number
  finalAmount?: number
  // Sprint 237：依店鋪分組的結帳摘要。購物車可以放多家店鋪的項目，但結帳、促銷碼、運費都以「一家店鋪」為單位
  stores?: StoreSummary[]
}

/** 沒有店鋪資訊的購物車（舊版回應）用這個鍵存放促銷碼的輸入狀態。 */
const LEGACY_PROMO_KEY = '_cart'

function formatPrice(price: number) {
  return new Intl.NumberFormat('zh-TW', { style: 'currency', currency: 'TWD' }).format(price)
}

function formatDate(dateStr: string | null) {
  if (!dateStr) return null
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-TW', { year: 'numeric', month: 'numeric', day: 'numeric' })
}

interface CartSummaryCardProps {
  title: string
  itemCount: number
  subtotal: number
  shippingFee: number
  appliedPromoCode?: string | null
  discountAmount: number
  testIdSuffix?: string
  /** 店鋪暫停營業（Sprint 239）：顯示說明並停用「前往結帳」 */
  storeClosed?: boolean
  promoInput: string
  promoLoading: boolean
  promoError?: string | null
  promoSuccess?: string | null
  onPromoInputChange: (value: string) => void
  onApplyPromo: () => void
  onRemovePromo: () => void
  onCheckout: () => void
}

/** 訂單摘要：小計、運費、優惠券、總金額與「前往結帳」。單一店鋪（或舊版購物車）整車一張，多家店鋪每家一張。 */
function CartSummaryCard(props: CartSummaryCardProps) {
  // 應付金額一律由小計＋運費－折扣算出，不採用伺服器上次回傳的 finalAmount：
  // 調整數量或移除項目後 finalAmount 會過期，而畫面上的小計是即時更新的
  const total = props.subtotal + props.shippingFee - props.discountAmount
  // 多家店鋪時每張摘要卡以 storeId 區分；整車一張摘要（單一店鋪／舊版購物車）沒有後綴
  const totalTestId = props.testIdSuffix ? `cart-store-total-${props.testIdSuffix}` : 'cart-total'
  const checkoutTestId = props.testIdSuffix ? `cart-store-checkout-${props.testIdSuffix}` : 'cart-checkout'
  return (
    <Card>
      <CardHeader>
        <CardTitle>{props.title}</CardTitle>
        <CardDescription>共 {props.itemCount} 項商品</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-2">
          <div className="flex justify-between">
            <span className="text-gray-600">小計</span>
            <span>{formatPrice(props.subtotal)}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-gray-600">運費</span>
            <span>{props.shippingFee > 0 ? formatPrice(props.shippingFee) : '免運'}</span>
          </div>
          {props.appliedPromoCode && (
            <div className="flex justify-between text-green-600">
              <span>優惠折抵</span>
              <span>-{formatPrice(props.discountAmount)}</span>
            </div>
          )}
          <div className="flex justify-between text-lg font-medium border-t pt-2">
            <span>總金額</span>
            <span className="text-2xl" data-testid={totalTestId}>{formatPrice(total)}</span>
          </div>
        </div>

        {/* Promo Code Section */}
        <div className="border-t pt-4">
          {!props.appliedPromoCode ? (
            <div className="flex gap-2">
              <Input
                placeholder="輸入優惠券代碼"
                value={props.promoInput}
                onChange={(e) => props.onPromoInputChange(e.target.value)}
                disabled={props.promoLoading}
              />
              <Button
                variant="outline"
                onClick={props.onApplyPromo}
                disabled={props.promoLoading || !props.promoInput.trim()}
              >
                {props.promoLoading ? '驗證中...' : '套用'}
              </Button>
            </div>
          ) : (
            <div className="flex items-center justify-between">
              <Badge variant="success" className="text-sm">
                已套用: {props.appliedPromoCode}
              </Badge>
              <Button
                variant="ghost"
                size="sm"
                onClick={props.onRemovePromo}
                disabled={props.promoLoading}
                className="text-red-500 hover:text-red-700"
              >
                移除
              </Button>
            </div>
          )}
          {props.promoError && <p className="text-sm text-red-500 mt-1">{props.promoError}</p>}
          {props.promoSuccess && <p className="text-sm text-green-600 mt-1">{props.promoSuccess}</p>}
        </div>

        {props.storeClosed && (
          <p className="text-sm text-red-600" data-testid={`${checkoutTestId}-closed`}>
            此店鋪目前暫停營業，無法下單。請移除這家店鋪的商品，或等店鋪恢復營業。
          </p>
        )}
        <Button
          className="w-full"
          size="lg"
          onClick={props.onCheckout}
          disabled={props.storeClosed}
          data-testid={checkoutTestId}
        >
          前往結帳
        </Button>
      </CardContent>
    </Card>
  )
}

export default function CartPage() {
  const router = useRouter()
  const [cart, setCart] = useState<CartResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [updating, setUpdating] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  // 促銷碼屬於店鋪（Sprint 237）：輸入框與訊息以店鋪為單位，鍵是 storeId（舊版購物車用 LEGACY_PROMO_KEY）
  const [promoInputs, setPromoInputs] = useState<Record<string, string>>({})
  const [promoLoadingKey, setPromoLoadingKey] = useState<string | null>(null)
  const [promoErrors, setPromoErrors] = useState<Record<string, string | null>>({})
  const [promoSuccesses, setPromoSuccesses] = useState<Record<string, string | null>>({})

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

  /**
   * 項目變動後在本地重算：總額與（若有）每家店鋪的小計／件數。
   * 運費與折扣仍沿用伺服器上次回傳的值（它們依店鋪運費模板與促銷碼而定，要等下次載入才會重算）。
   */
  const withItems = (prev: CartResponse, items: CartItem[]): CartResponse => ({
    ...prev,
    items,
    totalAmount: items.reduce((sum, item) => sum + item.subtotal, 0),
    stores: prev.stores
      ?.map((store) => {
        const storeItems = items.filter((item) => item.storeId === store.storeId)
        return {
          ...store,
          itemCount: storeItems.reduce((sum, item) => sum + item.quantity, 0),
          totalAmount: storeItems.reduce((sum, item) => sum + item.subtotal, 0),
        }
      })
      .filter((store) => store.itemCount > 0),
  })

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
        return withItems(
          prev,
          prev.items.map(item =>
            item.cartItemKey === cartItemKey
              ? { ...item, quantity: response.data.data.quantity, subtotal: response.data.data.subtotal }
              : item
          )
        )
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
          ...withItems(prev, remainingItems),
          totalItems: remainingItems.length
        }
      })
    } catch (err: unknown) {
      console.error('Failed to remove item:', err)
      setError('移除商品失敗，請稍後再試')
    } finally {
      setUpdating(null)
    }
  }

  const handleApplyPromo = async (key: string, storeId?: string) => {
    const promoCode = promoInputs[key] ?? ''
    if (!promoCode.trim() || !cart) return

    setPromoLoadingKey(key)
    setPromoErrors(prev => ({ ...prev, [key]: null }))
    setPromoSuccesses(prev => ({ ...prev, [key]: null }))

    try {
      // First validate（促銷碼在店鋪驗證：多家店鋪時要指定是哪一家）
      const validateResponse = await apiClient.get<{ data: { valid: boolean; invalidReason?: string } }>(
        `${API_ENDPOINTS.cart.validatePromo}?code=${encodeURIComponent(promoCode)}${storeId ? `&storeId=${storeId}` : ''}`
      )

      const validation = validateResponse.data.data
      if (!validation.valid) {
        setPromoErrors(prev => ({ ...prev, [key]: validation.invalidReason || '優惠券無效' }))
        setPromoLoadingKey(null)
        return
      }

      // Then apply
      const applyResponse = await apiClient.post<{ data: { storeId?: string; appliedPromoCode: string; shippingFee: number; discountAmount: number; finalAmount: number } }>(
        API_ENDPOINTS.cart.applyPromo,
        storeId ? { promoCode, storeId } : { promoCode }
      )

      const result = applyResponse.data.data
      const appliedStoreId = storeId ?? result.storeId
      setCart(prev => {
        if (!prev) return prev
        const stores = prev.stores?.map(store =>
          store.storeId === appliedStoreId
            ? {
                ...store,
                appliedPromoCode: result.appliedPromoCode,
                shippingFee: result.shippingFee,
                discountAmount: result.discountAmount,
                finalAmount: result.finalAmount,
              }
            : store
        )
        // 單一店鋪（或舊版購物車）時頂層欄位就是這家店鋪的摘要，一併更新
        const singleStore = !prev.stores || prev.stores.length <= 1
        return {
          ...prev,
          stores,
          ...(singleStore
            ? {
                appliedPromoCode: result.appliedPromoCode,
                shippingFee: result.shippingFee,
                discountAmount: result.discountAmount,
                finalAmount: result.finalAmount,
              }
            : {}),
        }
      })

      setPromoSuccesses(prev => ({ ...prev, [key]: `已套用優惠券，折扣 ${formatPrice(result.discountAmount)}` }))
      setPromoInputs(prev => ({ ...prev, [key]: '' }))
    } catch (err: unknown) {
      console.error('Failed to apply promo:', err)
      const code = (err as { response?: { data?: { code?: string } } })?.response?.data?.code
      setPromoErrors(prev => ({
        ...prev,
        [key]: code === 'E-5020' ? '購物車含多家店鋪的商品，請在要使用優惠券的店鋪區塊套用' : '優惠券套用失敗，請稍後再試',
      }))
    } finally {
      setPromoLoadingKey(null)
    }
  }

  const handleRemovePromo = async (key: string, storeId?: string) => {
    if (!cart) return

    setPromoLoadingKey(key)
    setPromoErrors(prev => ({ ...prev, [key]: null }))

    try {
      await apiClient.delete(API_ENDPOINTS.cart.removePromo, storeId ? { params: { storeId } } : undefined)

      setCart(prev => {
        if (!prev) return prev
        const stores = prev.stores?.map(store =>
          store.storeId === storeId
            ? { ...store, appliedPromoCode: undefined, discountAmount: 0 }
            : store
        )
        const singleStore = !prev.stores || prev.stores.length <= 1
        return {
          ...prev,
          stores,
          ...(singleStore
            ? {
                appliedPromoCode: undefined,
                discountAmount: 0,
                // 移除券後仍要收運費，不可直接清空 finalAmount 讓畫面退回「不含運費」的小計
                finalAmount: prev.totalAmount + (prev.shippingFee ?? 0),
              }
            : {}),
        }
      })

      setPromoSuccesses(prev => ({ ...prev, [key]: null }))
    } catch (err: unknown) {
      console.error('Failed to remove promo:', err)
      setPromoErrors(prev => ({ ...prev, [key]: '移除優惠券失敗，請稍後再試' }))
    } finally {
      setPromoLoadingKey(null)
    }
  }

  /**
   * 前往結帳。Sprint 127（DEF-048 擴大範圍）：這批項目同時有 PRODUCT 與 ROOM 時導向合併結帳頁一次結清兩者；
   * 純單一類型仍走原有兩條各自獨立的結帳頁（該頁僅處理對應類型項目，另一類項目會保留在購物車）。
   * Sprint 237（同店結帳）：結帳一次只結一家店鋪，結帳頁以 storeId 只取那家店鋪的項目；沒有店鋪資訊的舊版購物車不帶。
   */
  const goCheckout = (items: CartItem[], storeId?: string) => {
    const hasProduct = items.some((i) => i.listingType === 'PRODUCT')
    const hasRoom = items.some((i) => i.listingType === 'ROOM')
    const path = hasProduct && hasRoom ? '/checkout/mixed' : hasProduct ? '/checkout/product' : '/checkout'
    router.push(storeId ? `${path}?storeId=${storeId}` : path)
  }

  const renderItem = (item: CartItem) => (
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
  )

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

  // Sprint 237：依店鋪分組。沒有 stores（舊版回應）、或只有一家店鋪時維持原本「整車一張摘要」的畫面；
  // 含多家店鋪時，每家店鋪一個區塊、各自的摘要與結帳按鈕（訂單只能包含同一家店鋪的商品，PRD US-008／PC-005）
  const stores = cart?.stores ?? []
  const storeIds = new Set(stores.map(store => store.storeId))
  const groups = stores
    .map(store => ({ store, items: (cart?.items ?? []).filter(item => item.storeId === store.storeId) }))
    .filter(group => group.items.length > 0)
  const looseItems = (cart?.items ?? []).filter(item => !item.storeId || !storeIds.has(item.storeId))
  const isMultiStore = groups.length > 1
  const singleStore = groups.length === 1 && looseItems.length === 0 ? groups[0].store : null

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
          ) : isMultiStore ? (
            <div className="space-y-8">
              <Alert>
                <AlertDescription>
                  購物車含多家店鋪的商品。每筆訂單只能包含同一家店鋪的商品，請分店鋪各自結帳。
                </AlertDescription>
              </Alert>

              {groups.map(({ store, items }) => (
                <section key={store.storeId} data-testid={`cart-store-${store.storeId}`} className="space-y-4">
                  <h2 className="text-lg font-semibold text-gray-900">店鋪：{store.storeName || '未命名店鋪'}</h2>
                  <div className="space-y-4">{items.map(renderItem)}</div>
                  <CartSummaryCard
                    title={`訂單摘要（${store.storeName || '未命名店鋪'}）`}
                    itemCount={store.itemCount}
                    subtotal={store.totalAmount}
                    shippingFee={store.shippingFee}
                    appliedPromoCode={store.appliedPromoCode}
                    discountAmount={store.discountAmount}
                    testIdSuffix={store.storeId}
                    storeClosed={store.storeActive === false}
                    promoInput={promoInputs[store.storeId] ?? ''}
                    promoLoading={promoLoadingKey === store.storeId}
                    promoError={promoErrors[store.storeId]}
                    promoSuccess={promoSuccesses[store.storeId]}
                    onPromoInputChange={(value) => setPromoInputs(prev => ({ ...prev, [store.storeId]: value }))}
                    onApplyPromo={() => handleApplyPromo(store.storeId, store.storeId)}
                    onRemovePromo={() => handleRemovePromo(store.storeId, store.storeId)}
                    onCheckout={() => goCheckout(items, store.storeId)}
                  />
                </section>
              ))}

              {looseItems.length > 0 && (
                <section className="space-y-4" data-testid="cart-store-unavailable">
                  <h2 className="text-lg font-semibold text-gray-900">無法結帳的項目</h2>
                  <p className="text-sm text-gray-600">這些項目所屬的商品或房源已不存在，請移除。</p>
                  <div className="space-y-4">{looseItems.map(renderItem)}</div>
                </section>
              )}

              {/* Continue Shopping */}
              <div className="text-center">
                <Link href="/">
                  <Button variant="link" className="text-gray-600">
                    繼續購物
                  </Button>
                </Link>
              </div>
            </div>
          ) : (
            <div className="space-y-4">
              {singleStore && (
                <p className="text-sm text-gray-600">店鋪：{singleStore.storeName || '未命名店鋪'}</p>
              )}

              {/* Cart Items */}
              <div className="space-y-4">
                {cart.items.map(renderItem)}
              </div>

              {/* Cart Summary */}
              <CartSummaryCard
                title="訂單摘要"
                itemCount={cart.totalItems}
                storeClosed={singleStore?.storeActive === false}
                subtotal={cart.totalAmount}
                shippingFee={cart.shippingFee ?? 0}
                appliedPromoCode={cart.appliedPromoCode}
                discountAmount={cart.discountAmount || 0}
                promoInput={promoInputs[singleStore?.storeId ?? LEGACY_PROMO_KEY] ?? ''}
                promoLoading={promoLoadingKey === (singleStore?.storeId ?? LEGACY_PROMO_KEY)}
                promoError={promoErrors[singleStore?.storeId ?? LEGACY_PROMO_KEY]}
                promoSuccess={promoSuccesses[singleStore?.storeId ?? LEGACY_PROMO_KEY]}
                onPromoInputChange={(value) =>
                  setPromoInputs(prev => ({ ...prev, [singleStore?.storeId ?? LEGACY_PROMO_KEY]: value }))
                }
                onApplyPromo={() => handleApplyPromo(singleStore?.storeId ?? LEGACY_PROMO_KEY, singleStore?.storeId)}
                onRemovePromo={() => handleRemovePromo(singleStore?.storeId ?? LEGACY_PROMO_KEY, singleStore?.storeId)}
                onCheckout={() => goCheckout(cart.items, singleStore?.storeId)}
              />

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
