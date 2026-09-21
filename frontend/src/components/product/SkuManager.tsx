'use client'

import { useState, useEffect } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import SkuService, { Sku, SkuStatus } from '@/services/sku'

// 商品規格（SKU）管理（Sprint 178）。
// 背景：product_skus/product_inventory 先前在正式環境完全無法產生資料，導致依賴 SKU 的既有 ERP
// 庫存子系統（低庫存預警、採購單收貨入庫）實質上從未真正運作過。此元件是唯一缺少的建立入口，
// 建立後續由既有的採購單收貨/庫存異動流程接手增減數量，本元件本身不提供調整數量的功能。

function extractErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === 'object' && 'response' in err) {
    const axiosErr = err as { response?: { data?: { message?: string } } }
    return axiosErr.response?.data?.message || fallback
  }
  return fallback
}

interface SkuManagerProps {
  listingId: string
}

export default function SkuManager({ listingId }: SkuManagerProps) {
  const [skus, setSkus] = useState<Sku[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [toggleLoading, setToggleLoading] = useState<string | null>(null)

  useEffect(() => {
    fetchSkus()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [listingId])

  const fetchSkus = async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await SkuService.list(listingId)
      setSkus(result)
    } catch (err: unknown) {
      setError(extractErrorMessage(err, '載入商品規格失敗'))
    } finally {
      setLoading(false)
    }
  }

  const handleToggleStatus = async (sku: Sku) => {
    const nextStatus: SkuStatus = sku.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
    setToggleLoading(sku.id)
    try {
      await SkuService.update(listingId, sku.id, { status: nextStatus })
      fetchSkus()
    } catch (err: unknown) {
      alert(extractErrorMessage(err, '更新狀態失敗'))
    } finally {
      setToggleLoading(null)
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex justify-between items-center">
          <div>
            <CardTitle className="text-lg">商品規格（SKU）</CardTitle>
            <p className="text-sm text-muted-foreground mt-1">
              建立規格後才能在採購單中選擇此商品的品項並追蹤庫存數量
            </p>
          </div>
          <Button size="sm" onClick={() => setShowForm(true)} data-testid="sku-add">
            新增規格
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        {showForm && (
          <SkuCreateForm
            listingId={listingId}
            onClose={() => setShowForm(false)}
            onSaved={fetchSkus}
          />
        )}

        {loading ? (
          <div className="text-sm text-gray-500 py-4">載入中...</div>
        ) : error ? (
          <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md text-sm">
            {error}
          </div>
        ) : skus.length === 0 ? (
          <p className="text-sm text-gray-500 py-4">尚未建立任何規格</p>
        ) : (
          <div className="space-y-2">
            {skus.map((sku) => (
              <div
                key={sku.id}
                data-testid="sku-row"
                className="flex items-center justify-between border rounded-md px-3 py-2 text-sm"
              >
                <div className="flex items-center gap-3">
                  <span className="font-mono">{sku.skuCode}</span>
                  {sku.specName && <span className="text-gray-600">{sku.specName}</span>}
                  <Badge variant={sku.status === 'ACTIVE' ? 'success' : 'secondary'}>
                    {sku.status === 'ACTIVE' ? '啟用中' : '已停用'}
                  </Badge>
                </div>
                <div className="flex items-center gap-4">
                  <span className="text-gray-600">
                    可售 {sku.availableQty}（總量 {sku.totalQty} / 預留 {sku.reservedQty}）
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => handleToggleStatus(sku)}
                    disabled={toggleLoading === sku.id}
                  >
                    {toggleLoading === sku.id ? '處理中...' : sku.status === 'ACTIVE' ? '停用' : '啟用'}
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

function SkuCreateForm({
  listingId,
  onClose,
  onSaved,
}: {
  listingId: string
  onClose: () => void
  onSaved: () => void
}) {
  const [skuCode, setSkuCode] = useState('')
  const [specName, setSpecName] = useState('')
  const [priceOverride, setPriceOverride] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!skuCode.trim()) {
      setError('請填寫 SKU 代碼')
      return
    }

    setLoading(true)
    try {
      await SkuService.create(listingId, {
        skuCode: skuCode.trim(),
        ...(specName.trim() ? { specName: specName.trim() } : {}),
        ...(priceOverride !== '' ? { priceOverride: Number(priceOverride) } : {}),
      })
      onSaved()
      onClose()
    } catch (err: unknown) {
      setError(extractErrorMessage(err, '建立規格失敗'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="mb-4 border rounded-md p-4 space-y-3 bg-gray-50">
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-600 px-3 py-2 rounded-md text-sm">
          {error}
        </div>
      )}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <div>
          <label className="text-sm font-medium">SKU 代碼 <span className="text-red-500">*</span></label>
          <Input
            value={skuCode}
            onChange={(e) => setSkuCode(e.target.value)}
            placeholder="例如：SHIRT-RED-L"
            className="mt-1"
            data-testid="sku-code"
          />
        </div>
        <div>
          <label className="text-sm font-medium">規格名稱</label>
          <Input
            value={specName}
            onChange={(e) => setSpecName(e.target.value)}
            placeholder="例如：紅色 / L"
            className="mt-1"
            data-testid="sku-spec-name"
          />
        </div>
        <div>
          <label className="text-sm font-medium">價格覆寫</label>
          <Input
            type="number"
            min="0.01"
            step="0.01"
            value={priceOverride}
            onChange={(e) => setPriceOverride(e.target.value)}
            placeholder="留空則沿用商品基本售價"
            className="mt-1"
            data-testid="sku-price-override"
          />
        </div>
      </div>
      <div className="flex gap-2">
        <Button type="submit" size="sm" disabled={loading}>
          {loading ? '建立中...' : '確認新增'}
        </Button>
        <Button type="button" variant="outline" size="sm" onClick={onClose}>
          取消
        </Button>
      </div>
    </form>
  )
}
