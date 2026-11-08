'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import {
  PurchaseOrderCreateRequest,
  PurchaseOrderReceiveRequest,
  ListingOption,
  POStatus,
} from '@/services/erp/purchaseOrder'
import SupplierService, { SupplierDto } from '@/services/erp/supplier'
import PurchaseOrderService, { type PurchaseOrderDto } from '@/services/erp/purchaseOrder'
import { useAsyncData } from '@/hooks/useAsyncData'
import { Label } from '@/components/ui/label'
import { NativeSelect } from '@/components/ui/native-select'
import { Table, TableHeader, TableRow, TableHead, TableBody, TableFooter, TableCell } from '@/components/ui/table'
import { LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'
import { toast } from '@/components/ui/toast'
import { confirmDialog } from '@/components/ui/confirm-dialog'
import { StatusBadge } from '@/components/ui/status-badge'
import { apiErrorMessage } from '@/lib/api-error'

interface PurchaseOrderFormProps {
  orderId?: string
  mode: 'create' | 'edit' | 'view' | 'receive'
}

interface OrderItem {
  id: string
  listingId: string
  skuId: string
  skuCode: string
  productName: string
  quantity: number
  receivedQuantity: number
  receiveQty?: number
  unitPrice: number
  subtotal: number
}

// 檢視／編輯／收貨模式先載入採購單，載入完成才掛載表單：表單初值由載入的資料一次帶入，之後是使用者的編輯。
// 建立模式不載入採購單，直接掛載空表單；載入失敗時仍顯示表單與錯誤訊息（與原本相同）。
// 登入守門在賣家外殼（SellerShell），這裡不再自己檢查。
export default function PurchaseOrderForm({ orderId, mode }: PurchaseOrderFormProps) {
  const { data: order, error: loadError, loading } = useAsyncData(
    () => PurchaseOrderService.getPurchaseOrder(orderId!),
    [orderId],
    { enabled: Boolean(orderId) && mode !== 'create', errorMessage: '載入採購訂單失敗' }
  )

  if (loading) {
    return (
      <LoadingState />
    )
  }

  return <PurchaseOrderFormBody orderId={orderId} mode={mode} order={order} loadError={loadError} />
}

function PurchaseOrderFormBody({
  orderId,
  mode,
  order,
  loadError,
}: PurchaseOrderFormProps & { order?: PurchaseOrderDto; loadError: string | null }) {
  const router = useRouter()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(loadError)

  // 建立模式才需要的下拉選項（供應商、可選商品）。這兩個是建單的必填欄位，載入失敗不能只記錄：
  // 下拉會只剩佔位選項，使用者無法建單也不知道為什麼（DEF-472）。失敗時在表單上方明講是哪一個失敗並提供重新載入。
  // 失敗訊息一律寫明是哪個清單（後端有給訊息就接在後面），不單獨顯示後端的訊息（那樣兩個都失敗時看不出各是哪一個）。
  const {
    data: suppliersData,
    error: suppliersError,
    reload: reloadSuppliers,
  } = useAsyncData(
    () => SupplierService.listSuppliers('ACTIVE'),
    [],
    {
      enabled: mode === 'create',
      errorMessage: (info) => (info.message ? `載入供應商失敗：${info.message}` : '載入供應商失敗'),
      onError: (_message, err) => console.error('Failed to fetch suppliers:', err),
    }
  )
  const suppliers: SupplierDto[] = suppliersData ?? []
  // DEF-076：前端原本沒有任何選擇 listing 的來源，建單品項的 listingId 永遠無法送出
  const {
    data: listingOptionsData,
    error: listingOptionsError,
    reload: reloadListingOptions,
  } = useAsyncData(
    () => PurchaseOrderService.listListingOptions(),
    [],
    {
      enabled: mode === 'create',
      errorMessage: (info) => (info.message ? `載入商品選項失敗：${info.message}` : '載入商品選項失敗'),
      onError: (_message, err) => console.error('Failed to fetch listing options:', err),
    }
  )
  const listingOptions: ListingOption[] = listingOptionsData ?? []
  const optionsErrors = [suppliersError, listingOptionsError].filter((message): message is string => message !== null)
  // 只重抓失敗的那一個（成功的清單不需要再打一次）
  const reloadFailedOptions = () => {
    if (suppliersError !== null) reloadSuppliers()
    if (listingOptionsError !== null) reloadListingOptions()
  }
  // 載入成功、但真的沒有任何啟用中的供應商：與「載入失敗」是兩回事（失敗時 suppliersData 沒有值，不會進這個分支）
  const noActiveSupplier = suppliersData !== undefined && suppliersData.length === 0

  const [formData, setFormData] = useState<PurchaseOrderCreateRequest>(() =>
    order && mode !== 'receive'
      ? {
          supplierId: order.supplierId,
          // DEF-078：expectedDeliveryDate 為選填欄位，既有（修復前建立的）採購單恆為 null
          expectedDeliveryDate: order.expectedDeliveryDate ? order.expectedDeliveryDate.split('T')[0] : '',
          notes: order.notes || '',
          items: order.items.map(item => ({
            listingId: item.listingId,
            skuId: item.skuId,
            quantity: item.quantity,
            unitCost: item.unitPrice,
          })),
        }
      : {
          supplierId: '',
          expectedDeliveryDate: '',
          notes: '',
          items: [],
        }
  )

  const [items, setItems] = useState<OrderItem[]>(() => {
    if (order && mode === 'receive') {
      return order.items.map(item => ({
        id: item.id,
        listingId: item.listingId,
        skuId: item.skuId,
        skuCode: item.skuCode,
        productName: item.productName,
        quantity: item.quantity,
        receivedQuantity: item.receivedQuantity,
        // 預設帶入尚未收到的剩餘量，使用者可再調整（DEF-071：原本沒有輸入框，只會把舊的已收數量原樣送回）
        receiveQty: Math.max(0, item.quantity - item.receivedQuantity),
        unitPrice: item.unitPrice,
        subtotal: item.subtotal,
      }))
    }
    if (order) return order.items
    return [
      { id: '', listingId: '', skuId: '', skuCode: '', productName: '', quantity: 1, receivedQuantity: 0, unitPrice: 0, subtotal: 0 },
    ]
  })

  const orderStatus: POStatus | null = order?.status ?? null
  const rejectionReason: string | null = order?.rejectionReason || null

  const handleSupplierChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setFormData(prev => ({ ...prev, supplierId: e.target.value }))
  }

  const handleDateChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData(prev => ({ ...prev, expectedDeliveryDate: e.target.value }))
  }

  const handleNotesChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData(prev => ({ ...prev, notes: e.target.value }))
  }

  const handleItemChange = (index: number, field: string, value: string | number) => {
    const newItems = [...items]
    newItems[index] = { ...newItems[index], [field]: value }
    if (field === 'quantity' || field === 'unitPrice') {
      newItems[index].subtotal = newItems[index].quantity * newItems[index].unitPrice
    }
    setItems(newItems)
  }

  // DEF-076：選擇 listing 後同步帶入名稱作為顯示用品名（選擇器新增前，品名只能手動輸入）
  // Sprint 178：換商品後先前選的 SKU 已不適用，一併清空；若該商品僅有一個規格則直接帶入，
  // 減少多一次點擊（多規格情境仍需使用者自行選擇，避免誤收錯規格）
  const handleListingChange = (index: number, listingId: string) => {
    const newItems = [...items]
    const listing = listingOptions.find(l => l.id === listingId)
    newItems[index] = {
      ...newItems[index],
      listingId,
      productName: listing ? listing.title : newItems[index].productName,
      skuId: listing && listing.skus.length === 1 ? listing.skus[0].id : '',
      skuCode: listing && listing.skus.length === 1 ? listing.skus[0].skuCode : '',
    }
    setItems(newItems)
  }

  // Sprint 178：帶入 SKU 代碼作為顯示用途，供「品名」欄旁的規格識別
  const handleSkuChange = (index: number, skuId: string) => {
    const newItems = [...items]
    const listing = listingOptions.find(l => l.id === newItems[index].listingId)
    const sku = listing?.skus.find(s => s.id === skuId)
    newItems[index] = { ...newItems[index], skuId, skuCode: sku ? sku.skuCode : '' }
    setItems(newItems)
  }

  // DEF-071：收貨模式的「本次收貨」數量，限制在 [0, 尚未收到的剩餘量] 區間
  const handleReceiveQtyChange = (index: number, value: number) => {
    const newItems = [...items]
    const remaining = newItems[index].quantity - newItems[index].receivedQuantity
    newItems[index] = { ...newItems[index], receiveQty: Math.min(Math.max(value, 0), remaining) }
    setItems(newItems)
  }

  const addItem = () => {
    setItems([...items, { id: '', listingId: '', skuId: '', skuCode: '', productName: '', quantity: 1, receivedQuantity: 0, unitPrice: 0, subtotal: 0 }])
  }

  const removeItem = (index: number) => {
    if (items.length > 1) {
      setItems(items.filter((_, i) => i !== index))
    }
  }

  const handleCreate = async () => {
    if (!formData.supplierId) {
      toast.error('請選擇供應商')
      return
    }
    if (items.length === 0 || !items[0].listingId) {
      toast.error('請至少新增一個品項')
      return
    }
    // Sprint 178：skuId 缺失時收貨不會真正入庫（見 SkuManager 元件說明），前端強制要求選擇規格
    const selectedItems = items.filter(item => item.listingId)
    if (selectedItems.some(item => !item.skuId)) {
      toast.error('請為每個品項選擇規格（SKU）；若商品尚未建立規格，請先至商品編輯頁新增')
      return
    }

    setLoading(true)
    setError(null)
    try {
      const request: PurchaseOrderCreateRequest = {
        supplierId: formData.supplierId,
        expectedDeliveryDate: formData.expectedDeliveryDate || undefined,
        notes: formData.notes,
        items: selectedItems.map(item => ({
          listingId: item.listingId,
          skuId: item.skuId,
          quantity: item.quantity,
          unitCost: item.unitPrice,
        })),
      }
      await PurchaseOrderService.createPurchaseOrder(request)
      toast.success('採購訂單建立成功')
      router.push('/dashboard/erp/purchase-orders')
    } catch (err: unknown) {
      setError(apiErrorMessage(err, '建立失敗'))
    } finally {
      setLoading(false)
    }
  }

  const handleSubmit = async () => {
    if (!orderId) return
    setLoading(true)
    setError(null)
    try {
      await PurchaseOrderService.submitPurchaseOrder(orderId)
      toast.success('採購訂單已提交')
      router.push('/dashboard/erp/purchase-orders')
    } catch (err: unknown) {
      setError(apiErrorMessage(err, '提交失敗'))
    } finally {
      setLoading(false)
    }
  }

  const handleReceive = async () => {
    if (!orderId) return
    // DEF-071：原本誤送 item.receivedQuantity（既有累計已收量，非本次要收的量），
    // 且送出的欄位是 skuId 而非後端要求的 itemId，導致收貨永遠 400 或收貨數量錯誤
    const receiveItems = items
      .filter(item => item.id && (item.receiveQty ?? 0) > 0)
      .map(item => ({
        itemId: item.id,
        receivedQuantity: item.receiveQty ?? 0,
      }))
    if (receiveItems.length === 0) {
      setError('請至少輸入一項本次收貨數量')
      return
    }
    setLoading(true)
    setError(null)
    try {
      const request: PurchaseOrderReceiveRequest = { items: receiveItems }
      await PurchaseOrderService.receivePurchaseOrder(orderId, request)
      toast.success('收貨確認成功')
      router.push('/dashboard/erp/purchase-orders')
    } catch (err: unknown) {
      setError(apiErrorMessage(err, '收貨失敗'))
    } finally {
      setLoading(false)
    }
  }

  const handleUpdate = async () => {
    if (!orderId) return
    setLoading(true)
    setError(null)
    try {
      await PurchaseOrderService.updatePurchaseOrder(orderId, {
        notes: formData.notes,
        expectedDeliveryDate: formData.expectedDeliveryDate || undefined,
      })
      toast.success('採購訂單已更新')
      router.push('/dashboard/erp/purchase-orders')
    } catch (err: unknown) {
      setError(apiErrorMessage(err, '更新失敗'))
    } finally {
      setLoading(false)
    }
  }

  const handleCancel = async () => {
    if (!orderId) return
    if (!(await confirmDialog({ message: '確定要取消此採購訂單嗎？', destructive: true }))) return
    setLoading(true)
    setError(null)
    try {
      await PurchaseOrderService.cancelPurchaseOrder(orderId)
      toast.success('採購訂單已取消')
      router.push('/dashboard/erp/purchase-orders')
    } catch (err: unknown) {
      setError(apiErrorMessage(err, '取消失敗'))
    } finally {
      setLoading(false)
    }
  }

  const isViewMode = mode === 'view'
  const isReceiveMode = mode === 'receive'
  const totalAmount = items.reduce((sum, item) => sum + item.subtotal, 0)

  // DEF-234：狀態轉換條件比照後端 PurchaseOrder.canSubmit()/canCancel()/canReceive()
  // （domain/model/inventory/PurchaseOrder.java），detail 頁先前完全沒有依狀態顯示這些操作入口，
  // 導致訂單建立後永遠卡在 DRAFT，收貨頁也無法從任何畫面點擊到達。
  const canSubmit = orderStatus === 'DRAFT'
  const canCancel = orderStatus === 'DRAFT' || orderStatus === 'SUBMITTED'
    || orderStatus === 'PENDING_APPROVAL' || orderStatus === 'APPROVED'
  const canReceive = orderStatus === 'SUBMITTED' || orderStatus === 'PARTIALLY_RECEIVED'
    || orderStatus === 'APPROVED'

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle>
            {mode === 'create' && '新增採購訂單'}
            {mode === 'edit' && '編輯採購訂單'}
            {mode === 'view' && '採購訂單詳情'}
            {mode === 'receive' && '收貨確認'}
          </CardTitle>
          {orderStatus && <StatusBadge domain="purchaseOrder" status={orderStatus} />}
        </div>
      </CardHeader>
      <CardContent className="space-y-6">
        {error && (
          <Alert variant="destructive">
            {error}
          </Alert>
        )}

        {optionsErrors.length > 0 && (
          <Alert variant="destructive" data-testid="po-options-error" className="space-y-2">
            {optionsErrors.map((message, index) => (
              <p key={index}>{message}</p>
            ))}
            <Button type="button" variant="outline" size="sm" onClick={reloadFailedOptions}>
              重新載入
            </Button>
          </Alert>
        )}

        {orderStatus === 'REJECTED' && rejectionReason && (
          <Alert variant="destructive">
            <p className="text-sm font-medium">駁回原因</p>
            <p className="text-sm mt-1">{rejectionReason}</p>
          </Alert>
        )}

        {mode !== 'create' && orderId && (
          <div className="bg-rs-bg-base p-4 rounded-md">
            <p className="text-sm text-rs-ink-muted">訂單編號：{(orderId as string).slice(0, 8)}...</p>
          </div>
        )}

        {(mode === 'create' || mode === 'edit') && (
          <>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>供應商 *</Label>
                {mode === 'create' ? (
                  <NativeSelect
                    value={formData.supplierId}
                    onChange={handleSupplierChange}
                  >
                    <option value="">請選擇供應商</option>
                    {suppliers.map(s => (
                      <option key={s.id} value={s.id}>{s.name}</option>
                    ))}
                  </NativeSelect>
                ) : (
                  <Input value={formData.supplierId} disabled />
                )}
                {mode === 'create' && noActiveSupplier && (
                  <p className="text-sm text-rs-ink-muted" data-testid="po-no-supplier-hint">
                    目前沒有啟用中的供應商，請先
                    <Link href="/dashboard/erp/suppliers/new" className="text-rs-primary hover:underline">新增供應商</Link>
                  </p>
                )}
              </div>

              <div className="space-y-2">
                <Label>預計到貨日期</Label>
                <Input
                  type="date"
                  value={formData.expectedDeliveryDate}
                  onChange={handleDateChange}
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label>備註</Label>
              <Input
                value={formData.notes}
                onChange={handleNotesChange}
                placeholder="選填"
              />
            </div>
          </>
        )}

        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <Label>訂貨品項</Label>
            {mode === 'create' && (
              <Button type="button" variant="outline" size="sm" onClick={addItem}>
                + 新增品項
              </Button>
            )}
          </div>

            <Table density="compact">
              <TableHeader>
                <TableRow>
                  <TableHead>商品</TableHead>
                  {mode === 'create' && (
                    <TableHead>規格（SKU）</TableHead>
                  )}
                  <TableHead>品名</TableHead>
                  <TableHead className="text-right">數量</TableHead>
                  <TableHead className="text-right">單價</TableHead>
                  <TableHead className="text-right">小計</TableHead>
                  {mode === 'receive' && (
                    <>
                      <TableHead className="text-right">已收</TableHead>
                      <TableHead className="text-right">本次收貨</TableHead>
                    </>
                  )}
                  {mode === 'create' && <TableHead className="w-16"></TableHead>}
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.map((item, index) => (
                  <TableRow key={index}>
                    <TableCell>
                      {isViewMode || mode === 'edit' ? (
                        <span className="text-sm">{item.skuCode || item.skuId}</span>
                      ) : mode === 'create' ? (
                        <NativeSelect
                          value={item.listingId}
                          onChange={(e) => handleListingChange(index, e.target.value)}
                        >
                          <option value="">請選擇商品</option>
                          {listingOptions.map(l => (
                            <option key={l.id} value={l.id}>{l.title}</option>
                          ))}
                        </NativeSelect>
                      ) : (
                        <Input
                          value={item.skuId}
                          onChange={(e) => handleItemChange(index, 'skuId', e.target.value)}
                          placeholder="SKU001"
                        />
                      )}
                    </TableCell>
                    {mode === 'create' && (
                      <TableCell>
                        {(() => {
                          const listing = listingOptions.find(l => l.id === item.listingId)
                          if (!item.listingId) {
                            return <span className="text-sm text-rs-disabled-text">請先選擇商品</span>
                          }
                          if (!listing || listing.skus.length === 0) {
                            return <span className="text-sm text-rs-error">尚未建立規格，請先至商品編輯頁新增</span>
                          }
                          return (
                            <NativeSelect
                              value={item.skuId}
                              onChange={(e) => handleSkuChange(index, e.target.value)}
                            >
                              <option value="">請選擇規格</option>
                              {listing.skus.map(s => (
                                <option key={s.id} value={s.id}>
                                  {s.skuCode}{s.specName ? ` - ${s.specName}` : ''}
                                </option>
                              ))}
                            </NativeSelect>
                          )
                        })()}
                      </TableCell>
                    )}
                    <TableCell>
                      {isViewMode || mode === 'edit' ? (
                        <span className="text-sm">{item.productName}</span>
                      ) : (
                        <Input
                          value={item.productName}
                          onChange={(e) => handleItemChange(index, 'productName', e.target.value)}
                          placeholder="商品名稱"
                        />
                      )}
                    </TableCell>
                    <TableCell>
                      {isViewMode || mode === 'edit' ? (
                        <span className="text-sm text-right">{item.quantity}</span>
                      ) : (
                        <Input
                          type="number"
                          min="1"
                          value={item.quantity}
                          onChange={(e) => handleItemChange(index, 'quantity', parseInt(e.target.value) || 0)}
                          className="w-20 text-right"
                        />
                      )}
                    </TableCell>
                    <TableCell>
                      {isViewMode || mode === 'edit' ? (
                        <span className="text-sm text-right">{item.unitPrice}</span>
                      ) : (
                        <Input
                          type="number"
                          min="0"
                          value={item.unitPrice}
                          onChange={(e) => handleItemChange(index, 'unitPrice', parseFloat(e.target.value) || 0)}
                          className="w-24 text-right"
                        />
                      )}
                    </TableCell>
                    <TableCell className="text-right font-medium">
                      {item.subtotal.toLocaleString('zh-TW')}
                    </TableCell>
                    {isReceiveMode && (
                      <>
                        <TableCell className="text-right">
                          <span className="text-sm">{item.receivedQuantity}</span>
                        </TableCell>
                        <TableCell className="text-right">
                          <Input
                            type="number"
                            min="0"
                            max={item.quantity - item.receivedQuantity}
                            value={item.receiveQty ?? 0}
                            onChange={(e) => handleReceiveQtyChange(index, parseInt(e.target.value) || 0)}
                            className="w-20 text-right"
                          />
                        </TableCell>
                      </>
                    )}
                    {mode === 'create' && (
                      <TableCell>
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          onClick={() => removeItem(index)}
                          disabled={items.length === 1}
                        >
                          ✕
                        </Button>
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
              <TableFooter>
                <TableRow>
                  <TableCell colSpan={mode === 'create' ? 5 : 4} className="text-right font-medium">總金額：</TableCell>
                  <TableCell className="text-right font-bold text-lg">
                    {totalAmount.toLocaleString('zh-TW')} 元
                  </TableCell>
                </TableRow>
              </TableFooter>
            </Table>
        </div>
      </CardContent>

      <CardFooter className="flex gap-2 flex-wrap">
        {mode === 'view' && (
          <>
            {canSubmit && (
              <Button onClick={handleSubmit} disabled={loading}>
                {loading ? '提交中...' : '提交訂單'}
              </Button>
            )}
            {canReceive && orderId && (
              <Link href={`/dashboard/erp/purchase-orders/${orderId}/receive`}>
                <Button>前往收貨</Button>
              </Link>
            )}
            {canCancel && (
              <Button variant="outline" onClick={handleCancel} disabled={loading}>
                取消訂單
              </Button>
            )}
            <Link href="/dashboard/erp/purchase-orders">
              <Button variant="outline">返回列表</Button>
            </Link>
          </>
        )}
        {mode === 'create' && (
          <>
            <Button onClick={handleCreate} disabled={loading}>
              {loading ? '建立中...' : '建立訂單'}
            </Button>
            <Button variant="outline" onClick={() => router.push('/dashboard/erp/purchase-orders')}>
              取消
            </Button>
          </>
        )}
        {mode === 'edit' && (
          <>
            <Button onClick={handleUpdate} disabled={loading}>
              {loading ? '更新中...' : '更新訂單'}
            </Button>
            <Button variant="outline" onClick={() => router.push('/dashboard/erp/purchase-orders')}>
              取消
            </Button>
          </>
        )}
        {mode === 'receive' && (
          <>
            <Button onClick={handleReceive} disabled={loading}>
              {loading ? '確認中...' : '確認收貨'}
            </Button>
            <Button variant="outline" onClick={() => router.push('/dashboard/erp/purchase-orders')}>
              取消
            </Button>
          </>
        )}
      </CardFooter>
    </Card>
  )
}