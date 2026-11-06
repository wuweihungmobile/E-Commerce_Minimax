'use client'

import { useEffect, useState } from 'react'
import AddressService, { type Address, type AddressInput } from '@/services/address'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { StorefrontShell } from '@/components/layout/StorefrontShell'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { EmptyState } from '@/components/ui/states'
import { confirmDialog } from '@/components/ui/confirm-dialog'
import { toast } from '@/components/ui/toast'
import { apiErrorMessage } from '@/lib/api-error'

const EMPTY_FORM: AddressInput = {
  recipientName: '',
  phone: '',
  postalCode: '',
  city: '',
  district: '',
  addressLine: '',
}

const PHONE_PATTERN = /^09\d{8}$/

export default function AddressesPage() {
  const [addresses, setAddresses] = useState<Address[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<AddressInput>(EMPTY_FORM)
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      try {
        const result = await AddressService.list()
        if (cancelled) return
        setAddresses(result)
      } catch {
        if (cancelled) return
        setError('無法載入地址簿，請稍後再試')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [])

  async function reload() {
    const result = await AddressService.list()
    setAddresses(result)
  }

  function startCreate() {
    setForm(EMPTY_FORM)
    setEditingId(null)
    setFormError(null)
    setShowForm(true)
  }

  function startEdit(address: Address) {
    setForm({
      recipientName: address.recipientName,
      phone: address.phone,
      postalCode: address.postalCode || '',
      city: address.city,
      district: address.district || '',
      addressLine: address.addressLine,
    })
    setEditingId(address.id)
    setFormError(null)
    setShowForm(true)
  }

  function cancelForm() {
    setShowForm(false)
    setEditingId(null)
    setForm(EMPTY_FORM)
    setFormError(null)
  }

  function validateForm(): string | null {
    if (!form.recipientName.trim()) return '收件人姓名為必填'
    if (!PHONE_PATTERN.test(form.phone.trim())) return '電話格式錯誤（需為 09 開頭 10 位數字）'
    if (!form.city.trim()) return '城市為必填'
    if (!form.addressLine.trim()) return '詳細地址為必填'
    return null
  }

  async function handleSubmit() {
    const validationError = validateForm()
    if (validationError) {
      setFormError(validationError)
      return
    }
    setSubmitting(true)
    setFormError(null)
    try {
      const payload: AddressInput = {
        recipientName: form.recipientName.trim(),
        phone: form.phone.trim(),
        postalCode: form.postalCode?.trim() || undefined,
        city: form.city.trim(),
        district: form.district?.trim() || undefined,
        addressLine: form.addressLine.trim(),
      }
      if (editingId) {
        await AddressService.update(editingId, payload)
      } else {
        await AddressService.create(payload)
      }
      cancelForm()
      await reload()
    } catch (err) {
      setFormError(apiErrorMessage(err, editingId ? '更新地址失敗' : '新增地址失敗'))
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDelete(address: Address) {
    if (!(await confirmDialog({ message: `確定要刪除「${address.recipientName}」的地址嗎？`, confirmLabel: '刪除', destructive: true }))) return
    try {
      await AddressService.remove(address.id)
      await reload()
    } catch (err) {
      toast.error(apiErrorMessage(err, '刪除地址失敗'))
    }
  }

  async function handleSetDefault(address: Address) {
    try {
      await AddressService.setDefault(address.id)
      await reload()
    } catch (err) {
      toast.error(apiErrorMessage(err, '設定預設地址失敗'))
    }
  }

  return (
    <StorefrontShell>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <PageTitle>收貨地址簿</PageTitle>
          <PageDescription>
            {loading ? '載入中…' : `共 ${addresses.length} 筆地址`}
          </PageDescription>
        </div>
        <Button onClick={startCreate}>新增地址</Button>
      </div>

      {error && (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {showForm && (
        <Card className="mb-6">
          <CardContent className="py-5">
            <h2 className="text-sm font-semibold mb-3">{editingId ? '編輯地址' : '新增地址'}</h2>
            {formError && (
              <Alert variant="destructive" className="mb-3">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            )}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div>
                <Label className="mb-1 block">收件人姓名 *</Label>
                <Input
                  value={form.recipientName}
                  onChange={(e) => setForm({ ...form, recipientName: e.target.value })}
                />
              </div>
              <div>
                <Label className="mb-1 block">電話 *</Label>
                <Input
                  value={form.phone}
                  onChange={(e) => setForm({ ...form, phone: e.target.value })}
                  placeholder="0912345678"
                />
              </div>
              <div>
                <Label className="mb-1 block">城市 *</Label>
                <Input
                  value={form.city}
                  onChange={(e) => setForm({ ...form, city: e.target.value })}
                />
              </div>
              <div>
                <Label className="mb-1 block">區域</Label>
                <Input
                  value={form.district}
                  onChange={(e) => setForm({ ...form, district: e.target.value })}
                />
              </div>
              <div>
                <Label className="mb-1 block">郵遞區號</Label>
                <Input
                  value={form.postalCode}
                  onChange={(e) => setForm({ ...form, postalCode: e.target.value })}
                />
              </div>
              <div className="md:col-span-2">
                <Label className="mb-1 block">詳細地址 *</Label>
                <Input
                  value={form.addressLine}
                  onChange={(e) => setForm({ ...form, addressLine: e.target.value })}
                />
              </div>
            </div>
            <div className="flex gap-2 mt-4">
              <Button onClick={handleSubmit} disabled={submitting}>
                {submitting ? '儲存中...' : '儲存'}
              </Button>
              <Button variant="outline" onClick={cancelForm}>
                取消
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {loading ? (
        <div className="space-y-4">
          {[0, 1].map((i) => (
            <Card key={i}>
              <CardContent className="py-6">
                <Skeleton className="h-5 w-40 mb-3" />
                <Skeleton className="h-4 w-24" />
              </CardContent>
            </Card>
          ))}
        </div>
      ) : addresses.length === 0 && !error ? (
        <Card>
          <EmptyState icon="📍" title="尚無收貨地址" description="新增常用地址，結帳時可快速選用" action={<Button onClick={startCreate}>新增地址</Button>} className="py-16" />
        </Card>
      ) : (
        <div className="space-y-4">
          {addresses.map((address) => (
            <Card key={address.id}>
              <CardContent className="py-5">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-sm font-medium text-rs-ink">{address.recipientName}</span>
                      <span className="text-sm text-rs-ink-muted">{address.phone}</span>
                      {address.isDefault && <Badge>預設地址</Badge>}
                    </div>
                    <p className="text-sm text-rs-ink-muted">
                      {address.postalCode ? address.postalCode + ' ' : ''}
                      {address.city}
                      {address.district || ''}
                      {address.addressLine}
                    </p>
                  </div>
                  <div className="flex shrink-0 gap-2">
                    {!address.isDefault && (
                      <Button variant="outline" size="sm" onClick={() => handleSetDefault(address)}>
                        設為預設
                      </Button>
                    )}
                    <Button variant="outline" size="sm" onClick={() => startEdit(address)}>
                      編輯
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => handleDelete(address)}>
                      刪除
                    </Button>
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </StorefrontShell>
  )
}
