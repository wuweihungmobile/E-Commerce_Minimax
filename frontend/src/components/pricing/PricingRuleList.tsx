'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import PricingService, { PricingRule, PricingRuleType } from '@/services/pricing'
import AuthService from '@/services/auth'
import PricingCalendarPreview from '@/components/pricing/PricingCalendarPreview'

/**
 * 各規則型別的 config 參數欄位（AI-2404）。key 對齊後端 jsonb config。
 * 早鳥/長住/末班車為本 Sprint 主軸折扣型；其餘型別亦提供對應單一參數編輯，
 * 取代原本固定送出空 config `{}` 的黑箱行為。
 */
const CONFIG_FIELDS: Record<PricingRuleType, { key: string; label: string; step: string; hint: string }[]> = {
  EARLY_BIRD: [
    { key: 'minDaysAhead', label: '提前預訂天數', step: '1', hint: '下單日需早於入住日至少 N 天' },
    { key: 'discountPercent', label: '折扣百分比 (%)', step: '0.1', hint: '例如 15 表示打 85 折' },
  ],
  LONG_STAY: [
    { key: 'minNights', label: '最少住宿晚數', step: '1', hint: '住滿 N 晚起適用（折扣隨晚數遞增，上限為下方值）' },
    { key: 'discountPercent', label: '折扣百分比上限 (%)', step: '0.1', hint: '例如 20 表示最多打 80 折' },
  ],
  LAST_MINUTE: [
    { key: 'maxDaysAhead', label: '臨近入住天數', step: '1', hint: '入住日前 N 天內（含）下單適用' },
    { key: 'discountPercent', label: '折扣百分比 (%)', step: '0.1', hint: '例如 25 表示打 75 折' },
  ],
  WEEKDAY_WEEKEND: [
    { key: 'weekendMultiplier', label: '週末加成倍率', step: '0.01', hint: '例如 1.2 表示週五/六/日加成 20%' },
  ],
  SEASONAL: [
    { key: 'multiplier', label: '季節加成倍率', step: '0.01', hint: '例如 1.5 表示旺季加成 50%' },
  ],
  MANUAL_OVERRIDE: [
    { key: 'price', label: '覆蓋價格', step: '1', hint: '直接指定每日價格' },
  ],
}

export default function PricingRuleList() {
  const router = useRouter()
  const [rules, setRules] = useState<PricingRule[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filterRoom, setFilterRoom] = useState<string>('')
  const [activeOnly, setActiveOnly] = useState(false)
  const [deleteLoading, setDeleteLoading] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [editingRule, setEditingRule] = useState<PricingRule | null>(null)

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
    fetchRules()
  }, [router, filterRoom, activeOnly])

  const fetchRules = async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await PricingService.getRules(filterRoom || undefined, activeOnly)
      setRules(response)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '載入定價規則失敗')
      } else {
        setError('載入定價規則失敗')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleDelete = async (id: string) => {
    if (!confirm('確定要刪除這個定價規則嗎？')) return

    setDeleteLoading(id)
    try {
      await PricingService.deleteRule(id)
      fetchRules()
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        alert(axiosErr.response?.data?.message || '刪除失敗')
      } else {
        alert('刪除失敗')
      }
    } finally {
      setDeleteLoading(null)
    }
  }

  const getRuleTypeLabel = (type: PricingRuleType) => {
    const labels: Record<PricingRuleType, string> = {
      WEEKDAY_WEEKEND: '平日/週末',
      SEASONAL: '季節性',
      EARLY_BIRD: '早鳥優惠',
      LONG_STAY: '長住優惠',
      MANUAL_OVERRIDE: '手動覆蓋',
      LAST_MINUTE: '最後一刻',
    }
    return labels[type] || type
  }

  const getStatusBadge = (isActive: boolean) => {
    return isActive
      ? <Badge variant="success">啟用中</Badge>
      : <Badge variant="secondary">已停用</Badge>
  }

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleDateString('zh-TW')
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
      {/* Header Actions */}
      <div className="flex flex-col sm:flex-row justify-between gap-4">
        <div className="flex-1 flex gap-4">
          <Input
            type="search"
            placeholder="篩選房源 ID..."
            value={filterRoom}
            onChange={(e) => setFilterRoom(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && fetchRules()}
            className="max-w-xs"
          />
          <Select value={activeOnly ? 'true' : 'false'} onValueChange={(v) => setActiveOnly(v === 'true')}>
            <SelectTrigger className="w-32">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="false">全部</SelectItem>
              <SelectItem value="true">僅啟用</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" onClick={fetchRules}>
            搜尋
          </Button>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => fetchRules()}>
            重新整理
          </Button>
          <Button onClick={() => { setEditingRule(null); setShowForm(true) }}>
            新增規則
          </Button>
        </div>
      </div>

      {/* Pricing Rule Form Modal */}
      {showForm && (
        <PricingRuleFormModal
          rule={editingRule}
          onClose={() => { setShowForm(false); setEditingRule(null) }}
          onSaved={fetchRules}
        />
      )}

      {/* 定價預覽：輸入房源 ID 篩選後，可預覽該房源套用規則後的每日價格（AI-2405，沿用既有元件） */}
      {filterRoom && (
        <div data-testid="pricing-preview">
          <PricingCalendarPreview roomListingId={filterRoom} />
        </div>
      )}

      {/* Rule List */}
      {rules.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center h-48">
            <p className="text-gray-500 mb-4">尚無定價規則</p>
            <Button onClick={() => { setEditingRule(null); setShowForm(true) }}>
              新增第一個規則
            </Button>
          </CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {rules.map((rule) => (
            <Card key={rule.ruleId}>
              <CardHeader>
                <div className="flex justify-between items-start">
                  <CardTitle className="text-lg">{rule.ruleName}</CardTitle>
                  {getStatusBadge(rule.isActive)}
                </div>
                <CardDescription>{getRuleTypeLabel(rule.ruleType as PricingRuleType)}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-2">
                <div className="text-sm">
                  <span className="text-muted-foreground">房源 ID：</span>
                  <span className="font-mono text-xs">{rule.roomListingId}</span>
                </div>
                <div className="text-sm">
                  <span className="text-muted-foreground">優先級：</span>
                  {rule.priority || 0}
                </div>
                <div className="text-sm">
                  <span className="text-muted-foreground">有效期：</span>
                  {formatDate(rule.validFrom)} ~ {formatDate(rule.validTo)}
                </div>
                <div className="text-xs text-muted-foreground">
                  建立時間：{formatDate(rule.createdAt)}
                </div>
              </CardContent>
              <CardFooter className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => { setEditingRule(rule); setShowForm(true) }}
                >
                  編輯
                </Button>
                <Button
                  variant="destructive"
                  size="sm"
                  onClick={() => handleDelete(rule.ruleId)}
                  disabled={deleteLoading === rule.ruleId}
                >
                  {deleteLoading === rule.ruleId ? '刪除中...' : '刪除'}
                </Button>
              </CardFooter>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}

function PricingRuleFormModal({ rule, onClose, onSaved }: { rule?: PricingRule | null; onClose: () => void; onSaved: () => void }) {
  const [formData, setFormData] = useState({
    roomListingId: rule?.roomListingId || '',
    ruleType: rule?.ruleType || 'WEEKDAY_WEEKEND' as PricingRuleType,
    ruleName: rule?.ruleName || '',
    priority: rule?.priority || 0,
    config: rule?.config || {},
    validFrom: rule?.validFrom || '',
    validTo: rule?.validTo || '',
    isActive: rule?.isActive ?? true,
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const config = formData.config as Record<string, unknown>
  const configFields = CONFIG_FIELDS[formData.ruleType as PricingRuleType] || []
  const configValue = (key: string): string => {
    const v = config[key]
    return v === undefined || v === null ? '' : String(v)
  }
  const setConfigField = (key: string, raw: string) => {
    setFormData((prev) => {
      const next = { ...(prev.config as Record<string, unknown>) }
      if (raw === '') {
        delete next[key]
      } else {
        next[key] = Number(raw)
      }
      return { ...prev, config: next }
    })
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!formData.roomListingId || !formData.ruleName || !formData.validFrom || !formData.validTo) {
      setError('請填寫所有必填欄位')
      return
    }

    for (const f of configFields) {
      const v = config[f.key]
      if (v === undefined || v === null || v === '') {
        setError('請填寫所有折扣/規則參數')
        return
      }
    }

    setLoading(true)
    try {
      if (rule) {
        await PricingService.updateRule(rule.ruleId, {
          ruleName: formData.ruleName,
          priority: formData.priority,
          config: formData.config,
          validFrom: formData.validFrom,
          validTo: formData.validTo,
          isActive: formData.isActive,
        })
      } else {
        await PricingService.createRule({
          roomListingId: formData.roomListingId,
          ruleType: formData.ruleType,
          ruleName: formData.ruleName,
          priority: formData.priority,
          config: formData.config,
          validFrom: formData.validFrom,
          validTo: formData.validTo,
          isActive: formData.isActive,
        })
      }
      onSaved()
      onClose()
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '儲存失敗')
      } else {
        setError('儲存失敗')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <Card className="w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
        <CardHeader>
          <CardTitle>{rule ? '編輯定價規則' : '新增定價規則'}</CardTitle>
          <CardDescription>設定房源的動態定價規則</CardDescription>
        </CardHeader>
        <form onSubmit={handleSubmit}>
          <CardContent className="space-y-4">
            {error && (
              <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">
                {error}
              </div>
            )}

            <div>
              <label className="text-sm font-medium">房源 ID <span className="text-red-500">*</span></label>
              <Input
                value={formData.roomListingId}
                onChange={(e) => setFormData({ ...formData, roomListingId: e.target.value })}
                placeholder="請輸入房源 ID"
                disabled={!!rule}
                className="mt-1"
              />
            </div>

            <div>
              <label className="text-sm font-medium">規則類型</label>
              <Select value={formData.ruleType} onValueChange={(v) => setFormData({ ...formData, ruleType: v as PricingRuleType, config: {} })} disabled={!!rule}>
                <SelectTrigger className="mt-1">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="WEEKDAY_WEEKEND">平日/週末</SelectItem>
                  <SelectItem value="SEASONAL">季節性</SelectItem>
                  <SelectItem value="EARLY_BIRD">早鳥優惠</SelectItem>
                  <SelectItem value="LONG_STAY">長住優惠</SelectItem>
                  <SelectItem value="MANUAL_OVERRIDE">手動覆蓋</SelectItem>
                  <SelectItem value="LAST_MINUTE">最後一刻</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div>
              <label className="text-sm font-medium">規則名稱 <span className="text-red-500">*</span></label>
              <Input
                value={formData.ruleName}
                onChange={(e) => setFormData({ ...formData, ruleName: e.target.value })}
                placeholder="例如：週末加成"
                className="mt-1"
              />
            </div>

            <div>
              <label className="text-sm font-medium">優先級</label>
              <Input
                type="number"
                value={formData.priority}
                onChange={(e) => setFormData({ ...formData, priority: parseInt(e.target.value) || 0 })}
                placeholder="0"
                className="mt-1"
              />
            </div>

            {configFields.length > 0 && (
              <div className="space-y-3 rounded-md border border-gray-100 bg-gray-50 p-3" data-testid="pricing-config-fields">
                <p className="text-sm font-medium">規則參數</p>
                {configFields.map((f) => (
                  <div key={f.key}>
                    <label className="text-sm font-medium">
                      {f.label} <span className="text-red-500">*</span>
                    </label>
                    <Input
                      type="number"
                      step={f.step}
                      value={configValue(f.key)}
                      onChange={(e) => setConfigField(f.key, e.target.value)}
                      className="mt-1"
                      data-testid={`pricing-config-${f.key}`}
                    />
                    <p className="mt-1 text-xs text-muted-foreground">{f.hint}</p>
                  </div>
                ))}
              </div>
            )}

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="text-sm font-medium">開始日期 <span className="text-red-500">*</span></label>
                <Input
                  type="date"
                  value={formData.validFrom}
                  onChange={(e) => setFormData({ ...formData, validFrom: e.target.value })}
                  className="mt-1"
                />
              </div>
              <div>
                <label className="text-sm font-medium">結束日期 <span className="text-red-500">*</span></label>
                <Input
                  type="date"
                  value={formData.validTo}
                  onChange={(e) => setFormData({ ...formData, validTo: e.target.value })}
                  className="mt-1"
                />
              </div>
            </div>

            <div className="flex items-center gap-2">
              <input
                type="checkbox"
                id="isActive"
                checked={formData.isActive}
                onChange={(e) => setFormData({ ...formData, isActive: e.target.checked })}
              />
              <label htmlFor="isActive" className="text-sm">啟用此規則</label>
            </div>
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