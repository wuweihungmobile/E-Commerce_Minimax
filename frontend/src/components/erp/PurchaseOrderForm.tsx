'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import {
  PurchaseOrderDto,
  PurchaseOrderCreateRequest,
  PurchaseOrderUpdateRequest,
  PurchaseOrderReceiveRequest,
  POStatus,
} from '@/services/erp/purchaseOrder'
import SupplierService, { SupplierDto } from '@/services/erp/supplier'
import PurchaseOrderService from '@/services/erp/purchaseOrder'
import AuthService from '@/services/auth'

interface PurchaseOrderFormProps {
  orderId?: string
  mode: 'create' | 'edit' | 'view' | 'receive'
}

interface OrderItem {
  skuId: string
  skuCode: string
  productName: string
  quantity: number
  receivedQuantity: number
  unitPrice: number
  subtotal: number
}

export default function PurchaseOrderForm({ orderId, mode }: PurchaseOrderFormProps) {
  const router = useRouter()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [initialLoading, setInitialLoading] = useState(orderId ? true : false)
  const [suppliers, setSuppliers] = useState<SupplierDto[]>([])

  const [formData, setFormData] = useState<PurchaseOrderCreateRequest>({
    supplierId: '',
    expectedDeliveryDate: '',
    notes: '',
    items: [],
  })

  const [items, setItems] = useState<OrderItem[]>([
    { skuId: '', skuCode: '', productName: '', quantity: 1, receivedQuantity: 0, unitPrice: 0, subtotal: 0 },
  ])

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }

    if (mode === 'create') {
      fetchSuppliers()
    } else if (orderId) {
      fetchOrder()
    }
  }, [router, orderId, mode])

  const fetchSuppliers = async () => {
    try {
      const data = await SupplierService.listSuppliers('ACTIVE')
      setSuppliers(data)
    } catch (err) {
      console.error('Failed to fetch suppliers:', err)
    }
  }

  const fetchOrder = async () => {
    if (!orderId) return
    setInitialLoading(true)
    try {
      const data = await PurchaseOrderService.getPurchaseOrder(orderId)
      if (mode === 'receive') {
        setItems(data.items.map(item => ({
          skuId: item.skuId,
          skuCode: item.skuCode,
          productName: item.productName,
          quantity: item.quantity,
          receivedQuantity: item.receivedQuantity,
          unitPrice: item.unitPrice,
          subtotal: item.subtotal,
        })))
      } else {
        setFormData({
          supplierId: data.supplierId,
          expectedDeliveryDate: data.expectedDeliveryDate.split('T')[0],
          notes: data.notes || '',
          items: data.items.map(item => ({
            skuId: item.skuId,
            quantity: item.quantity,
            unitPrice: item.unitPrice,
          })),
        })
        setItems(data.items)
      }
      if (mode === 'create') {
        fetchSuppliers()
      }
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '載入採購訂單失敗')
      } else {
        setError('載入採購訂單失敗')
      }
    } finally {
      setInitialLoading(false)
    }
  }

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

  const addItem = () => {
    setItems([...items, { skuId: '', skuCode: '', productName: '', quantity: 1, receivedQuantity: 0, unitPrice: 0, subtotal: 0 }])
  }

  const removeItem = (index: number) => {
    if (items.length > 1) {
      setItems(items.filter((_, i) => i !== index))
    }
  }

  const getStatusBadge = (status: POStatus) => {
    const config: Record<POStatus, { variant: 'default' | 'secondary' | 'destructive' | 'success' | 'outline' | 'warning'; label: string }> = {
      DRAFT: { variant: 'outline', label: '草稿' },
      SUBMITTED: { variant: 'default', label: '已提交' },
      PARTIAL_RECEIVED: { variant: 'warning', label: '部分到貨' },
      RECEIVED: { variant: 'success', label: '已到貨' },
      CANCELLED: { variant: 'destructive', label: '已取消' },
    }
    const c = config[status] || { variant: 'outline', label: status }
    return <span className={`px-2 py-1 text-xs rounded-full ${c.variant === 'success' ? 'bg-green-100 text-green-800' : c.variant === 'destructive' ? 'bg-red-100 text-red-800' : 'bg-gray-100 text-gray-800'}`}>{c.label}</span>
  }

  const handleCreate = async () => {
    if (!formData.supplierId) {
      alert('請選擇供應商')
      return
    }
    if (items.length === 0 || !items[0].skuId) {
      alert('請至少新增一個品項')
      return
    }

    setLoading(true)
    setError(null)
    try {
      const request: PurchaseOrderCreateRequest = {
        supplierId: formData.supplierId,
        expectedDeliveryDate: formData.expectedDeliveryDate,
        notes: formData.notes,
        items: items.filter(item => item.skuId).map(item => ({
          skuId: item.skuId,
          quantity: item.quantity,
          unitPrice: item.unitPrice,
        })),
      }
      await PurchaseOrderService.createPurchaseOrder(request)
      alert('採購訂單建立成功')
      router.push('/dashboard/erp/purchase-orders')
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '建立失敗')
      } else {
        setError('建立失敗')
      }
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
      alert('採購訂單已提交')
      router.push('/dashboard/erp/purchase-orders')
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '提交失敗')
      } else {
        setError('提交失敗')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleReceive = async () => {
    if (!orderId) return
    setLoading(true)
    setError(null)
    try {
      const request: PurchaseOrderReceiveRequest = {
        items: items.filter(item => item.skuId).map(item => ({
          skuId: item.skuId,
          receivedQuantity: item.receivedQuantity,
        })),
      }
      await PurchaseOrderService.receivePurchaseOrder(orderId, request)
      alert('收貨確認成功')
      router.push('/dashboard/erp/purchase-orders')
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '收貨失敗')
      } else {
        setError('收貨失敗')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleCancel = async () => {
    if (!orderId) return
    if (!confirm('確定要取消此採購訂單嗎？')) return
    setLoading(true)
    setError(null)
    try {
      await PurchaseOrderService.cancelPurchaseOrder(orderId)
      alert('採購訂單已取消')
      router.push('/dashboard/erp/purchase-orders')
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '取消失敗')
      } else {
        setError('取消失敗')
      }
    } finally {
      setLoading(false)
    }
  }

  if (initialLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">載入中...</div>
      </div>
    )
  }

  const isViewMode = mode === 'view'
  const isReceiveMode = mode === 'receive'
  const totalAmount = items.reduce((sum, item) => sum + item.subtotal, 0)

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          {mode === 'create' && '新增採購訂單'}
          {mode === 'edit' && '編輯採購訂單'}
          {mode === 'view' && '採購訂單詳情'}
          {mode === 'receive' && '收貨確認'}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        {error && (
          <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">
            {error}
          </div>
        )}

        {mode !== 'create' && orderId && (
          <div className="bg-gray-50 p-4 rounded-md">
            <p className="text-sm text-gray-600">訂單編號：{(orderId as string).slice(0, 8)}...</p>
          </div>
        )}

        {(mode === 'create' || mode === 'edit') && (
          <>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="space-y-2">
                <label className="text-sm font-medium">供應商 *</label>
                {mode === 'create' ? (
                  <select
                    value={formData.supplierId}
                    onChange={handleSupplierChange}
                    className="w-full border rounded-md px-3 py-2"
                  >
                    <option value="">請選擇供應商</option>
                    {suppliers.map(s => (
                      <option key={s.id} value={s.id}>{s.name}</option>
                    ))}
                  </select>
                ) : (
                  <Input value={formData.supplierId} disabled />
                )}
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium">預計到貨日期</label>
                <Input
                  type="date"
                  value={formData.expectedDeliveryDate}
                  onChange={handleDateChange}
                />
              </div>
            </div>

            <div className="space-y-2">
              <label className="text-sm font-medium">備註</label>
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
            <label className="text-sm font-medium">訂貨品項</label>
            {mode === 'create' && (
              <Button type="button" variant="outline" size="sm" onClick={addItem}>
                + 新增品項
              </Button>
            )}
          </div>

          <div className="border rounded-md">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-3 py-2 text-left text-xs font-medium text-gray-500">SKU</th>
                  <th className="px-3 py-2 text-left text-xs font-medium text-gray-500">品名</th>
                  <th className="px-3 py-2 text-right text-xs font-medium text-gray-500">數量</th>
                  <th className="px-3 py-2 text-right text-xs font-medium text-gray-500">單價</th>
                  <th className="px-3 py-2 text-right text-xs font-medium text-gray-500">小計</th>
                  {mode === 'receive' && (
                    <th className="px-3 py-2 text-right text-xs font-medium text-gray-500">已收</th>
                  )}
                  {mode === 'create' && <th className="w-16"></th>}
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {items.map((item, index) => (
                  <tr key={index}>
                    <td className="px-3 py-2">
                      {isViewMode || mode === 'edit' ? (
                        <span className="text-sm">{item.skuCode || item.skuId}</span>
                      ) : (
                        <Input
                          value={item.skuId}
                          onChange={(e) => handleItemChange(index, 'skuId', e.target.value)}
                          placeholder="SKU001"
                        />
                      )}
                    </td>
                    <td className="px-3 py-2">
                      {isViewMode || mode === 'edit' ? (
                        <span className="text-sm">{item.productName}</span>
                      ) : (
                        <Input
                          value={item.productName}
                          onChange={(e) => handleItemChange(index, 'productName', e.target.value)}
                          placeholder="商品名稱"
                        />
                      )}
                    </td>
                    <td className="px-3 py-2">
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
                    </td>
                    <td className="px-3 py-2">
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
                    </td>
                    <td className="px-3 py-2 text-right font-medium">
                      {item.subtotal.toLocaleString('zh-TW')}
                    </td>
                    {isReceiveMode && (
                      <td className="px-3 py-2 text-right">
                        <span className="text-sm">{item.receivedQuantity}</span>
                      </td>
                    )}
                    {mode === 'create' && (
                      <td className="px-3 py-2">
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          onClick={() => removeItem(index)}
                          disabled={items.length === 1}
                        >
                          ✕
                        </Button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
              <tfoot className="bg-gray-50">
                <tr>
                  <td colSpan={4} className="px-3 py-2 text-right font-medium">總金額：</td>
                  <td className="px-3 py-2 text-right font-bold text-lg">
                    {totalAmount.toLocaleString('zh-TW')} 元
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>
        </div>
      </CardContent>

      <CardFooter className="flex gap-2 flex-wrap">
        {mode === 'view' && (
          <>
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
            <Button onClick={() => {/* update */}} disabled={loading}>
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