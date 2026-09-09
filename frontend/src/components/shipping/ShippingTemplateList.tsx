'use client'

import { useState, useEffect } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import ShippingTemplateService, {
  ShippingTemplate,
  ShippingFeeType,
  SHIPPING_FEE_TYPE_LABELS,
} from '@/services/shippingTemplate'

function extractErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === 'object' && 'response' in err) {
    const axiosErr = err as { response?: { data?: { message?: string } } }
    return axiosErr.response?.data?.message || fallback
  }
  return fallback
}

function formatAmount(value: number | null): string {
  if (value === null || value === undefined) return '—'
  return new Intl.NumberFormat('zh-TW', { style: 'currency', currency: 'TWD', maximumFractionDigits: 0 }).format(value)
}

export default function ShippingTemplateList() {
  const [templates, setTemplates] = useState<ShippingTemplate[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [deleteLoading, setDeleteLoading] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [editingTemplate, setEditingTemplate] = useState<ShippingTemplate | null>(null)

  useEffect(() => {
    fetchTemplates()
  }, [])

  const fetchTemplates = async () => {
    setLoading(true)
    setError(null)
    try {
      const response = await ShippingTemplateService.list()
      setTemplates(response)
    } catch (err: unknown) {
      setError(extractErrorMessage(err, '載入運費模板失敗'))
    } finally {
      setLoading(false)
    }
  }

  const handleDelete = async (id: string) => {
    if (!confirm('確定要刪除這個運費模板嗎？刪除後，若無其他模板，訂單將恢復為一律免運費。')) return

    setDeleteLoading(id)
    try {
      await ShippingTemplateService.remove(id)
      fetchTemplates()
    } catch (err: unknown) {
      alert(extractErrorMessage(err, '刪除失敗'))
    } finally {
      setDeleteLoading(null)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">載入中...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">
        {error}
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between gap-4">
        <p className="text-sm text-gray-600 max-w-2xl">
          結帳時系統以此清單的<strong>第一筆</strong>模板計算運費；尚未建立任何模板時，訂單一律免運費。
        </p>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => fetchTemplates()}>
            重新整理
          </Button>
          <Button onClick={() => { setEditingTemplate(null); setShowForm(true) }} data-testid="shipping-template-add">
            新增運費模板
          </Button>
        </div>
      </div>

      {showForm && (
        <ShippingTemplateFormModal
          template={editingTemplate}
          onClose={() => { setShowForm(false); setEditingTemplate(null) }}
          onSaved={fetchTemplates}
        />
      )}

      {templates.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center h-48">
            <p className="text-gray-500 mb-4">尚無運費模板，全站訂單目前一律免運費</p>
            <Button onClick={() => { setEditingTemplate(null); setShowForm(true) }}>
              建立第一個運費模板
            </Button>
          </CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {templates.map((template, index) => (
            <Card key={template.id} data-testid="shipping-template-card">
              <CardHeader>
                <div className="flex justify-between items-start">
                  <CardTitle className="text-lg">{template.name}</CardTitle>
                  <div className="flex gap-2">
                    {index === 0 && <Badge variant="success">生效中</Badge>}
                    <Badge variant="secondary">{SHIPPING_FEE_TYPE_LABELS[template.feeType]}</Badge>
                  </div>
                </div>
              </CardHeader>
              <CardContent className="space-y-2">
                {template.feeType === 'FIXED' ? (
                  <div className="text-sm">
                    <span className="text-muted-foreground">固定運費：</span>
                    {formatAmount(template.fixedAmount)}
                  </div>
                ) : (
                  <>
                    <div className="text-sm">
                      <span className="text-muted-foreground">未達門檻運費：</span>
                      {formatAmount(template.fixedAmount)}
                    </div>
                    <div className="text-sm">
                      <span className="text-muted-foreground">免運門檻：</span>
                      訂單滿 {formatAmount(template.freeThreshold)}
                    </div>
                  </>
                )}
              </CardContent>
              <CardFooter className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => { setEditingTemplate(template); setShowForm(true) }}
                >
                  編輯
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={() => handleDelete(template.id)}
                  disabled={deleteLoading === template.id}
                >
                  {deleteLoading === template.id ? '刪除中...' : '刪除'}
                </Button>
              </CardFooter>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}

function ShippingTemplateFormModal({
  template,
  onClose,
  onSaved,
}: {
  template?: ShippingTemplate | null
  onClose: () => void
  onSaved: () => void
}) {
  const [name, setName] = useState(template?.name ?? '')
  const [feeType, setFeeType] = useState<ShippingFeeType>(template?.feeType ?? 'FIXED')
  const [fixedAmount, setFixedAmount] = useState(template?.fixedAmount != null ? String(template.fixedAmount) : '')
  const [freeThreshold, setFreeThreshold] = useState(
    template?.freeThreshold != null ? String(template.freeThreshold) : ''
  )
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!name.trim()) {
      setError('請填寫模板名稱')
      return
    }
    if (fixedAmount === '') {
      setError(feeType === 'FIXED' ? '請填寫固定運費金額' : '請填寫未達門檻時的運費金額')
      return
    }
    if (feeType === 'FREE_THRESHOLD' && freeThreshold === '') {
      setError('請填寫免運門檻金額')
      return
    }

    setLoading(true)
    try {
      if (template) {
        await ShippingTemplateService.update(template.id, {
          name,
          fixedAmount: Number(fixedAmount),
          ...(feeType === 'FREE_THRESHOLD' ? { freeThreshold: Number(freeThreshold) } : {}),
        })
      } else {
        await ShippingTemplateService.create({
          name,
          feeType,
          fixedAmount: Number(fixedAmount),
          ...(feeType === 'FREE_THRESHOLD' ? { freeThreshold: Number(freeThreshold) } : {}),
        })
      }
      onSaved()
      onClose()
    } catch (err: unknown) {
      setError(extractErrorMessage(err, '儲存失敗'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <Card className="w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
        <CardHeader>
          <CardTitle>{template ? '編輯運費模板' : '新增運費模板'}</CardTitle>
          <CardDescription>設定固定運費，或滿額免運門檻</CardDescription>
        </CardHeader>
        <form onSubmit={handleSubmit}>
          <CardContent className="space-y-4">
            {error && (
              <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">
                {error}
              </div>
            )}

            <div>
              <label className="text-sm font-medium">模板名稱 <span className="text-red-500">*</span></label>
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="例如：標準運費"
                className="mt-1"
                data-testid="shipping-template-name"
              />
            </div>

            <div>
              <label className="text-sm font-medium">計費方式</label>
              {/* 對齊後端 ShippingTemplateDto.UpdateRequest：不含 feeType，建立後類型不可變更 */}
              <Select
                value={feeType}
                onValueChange={(v) => setFeeType(v as ShippingFeeType)}
                disabled={!!template}
              >
                <SelectTrigger className="mt-1" data-testid="shipping-template-fee-type">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="FIXED">固定運費</SelectItem>
                  <SelectItem value="FREE_THRESHOLD">滿額免運</SelectItem>
                </SelectContent>
              </Select>
              {template && (
                <p className="mt-1 text-xs text-muted-foreground">計費方式建立後不可變更，如需更改請刪除後重新建立。</p>
              )}
            </div>

            <div>
              <label className="text-sm font-medium">
                {feeType === 'FIXED' ? '固定運費金額' : '未達門檻時的運費'} <span className="text-red-500">*</span>
              </label>
              <Input
                type="number"
                min="0"
                step="1"
                value={fixedAmount}
                onChange={(e) => setFixedAmount(e.target.value)}
                placeholder="0"
                className="mt-1"
                data-testid="shipping-template-fixed-amount"
              />
            </div>

            {feeType === 'FREE_THRESHOLD' && (
              <div>
                <label className="text-sm font-medium">免運門檻金額 <span className="text-red-500">*</span></label>
                <Input
                  type="number"
                  min="0"
                  step="1"
                  value={freeThreshold}
                  onChange={(e) => setFreeThreshold(e.target.value)}
                  placeholder="例如：1000"
                  className="mt-1"
                  data-testid="shipping-template-free-threshold"
                />
                <p className="mt-1 text-xs text-muted-foreground">訂單金額達到此門檻（含）即免運費。</p>
              </div>
            )}
          </CardContent>
          <CardFooter className="flex gap-2">
            <Button type="submit" disabled={loading}>
              {loading ? '儲存中...' : '儲存'}
            </Button>
            <Button type="button" variant="outline" onClick={onClose}>
              取消
            </Button>
          </CardFooter>
        </form>
      </Card>
    </div>
  )
}
