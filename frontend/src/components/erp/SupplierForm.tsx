'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import SupplierService, {
  SupplierCreateRequest,
  type SupplierDto,
} from '@/services/erp/supplier'
import { useAsyncData } from '@/hooks/useAsyncData'
import { Label } from '@/components/ui/label'
import { LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'
import { toast } from '@/components/ui/toast'
import { apiErrorMessage } from '@/lib/api-error'

interface SupplierFormProps {
  supplierId?: string
  mode: 'create' | 'edit' | 'view'
}

// 編輯／檢視模式先載入供應商，載入完成才掛載表單：表單初值由載入的資料一次帶入，之後是使用者的編輯。
// 新增模式（沒有 supplierId）不載入，直接掛載空表單；載入失敗時仍顯示表單與錯誤訊息（與原本相同）。
// 登入守門在賣家外殼（SellerShell），這裡不再自己檢查。
export default function SupplierForm({ supplierId, mode }: SupplierFormProps) {
  const { data: supplier, error: loadError, loading } = useAsyncData(
    () => SupplierService.getSupplier(supplierId!),
    [supplierId],
    { enabled: Boolean(supplierId), errorMessage: '載入供應商失敗' }
  )

  if (loading) {
    return (
      <LoadingState />
    )
  }

  return <SupplierFormBody supplierId={supplierId} mode={mode} supplier={supplier} loadError={loadError} />
}

function SupplierFormBody({
  supplierId,
  mode,
  supplier,
  loadError,
}: SupplierFormProps & { supplier?: SupplierDto; loadError: string | null }) {
  const router = useRouter()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(loadError)

  const [formData, setFormData] = useState<SupplierCreateRequest>(() => ({
    name: supplier?.name ?? '',
    contactPerson: supplier?.contactPerson || '',
    email: supplier?.email || '',
    phone: supplier?.phone || '',
    address: supplier?.address || '',
  }))

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target
    setFormData(prev => ({ ...prev, [name]: value }))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!formData.name.trim()) {
      toast.error('請輸入供應商名稱')
      return
    }

    setLoading(true)
    setError(null)

    try {
      if (mode === 'create') {
        await SupplierService.createSupplier(formData)
        toast.success('供應商建立成功')
        router.push('/dashboard/erp/suppliers')
      } else if (supplierId) {
        await SupplierService.updateSupplier(supplierId, formData)
        toast.success('供應商更新成功')
        router.push('/dashboard/erp/suppliers')
      }
    } catch (err: unknown) {
      setError(apiErrorMessage(err, '操作失敗'))
    } finally {
      setLoading(false)
    }
  }

  const isViewMode = mode === 'view'

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          {mode === 'create' && '新增供應商'}
          {mode === 'edit' && '編輯供應商'}
          {mode === 'view' && '供應商詳情'}
        </CardTitle>
        {supplierId && mode !== 'view' && (
          <CardDescription>ID: {supplierId}</CardDescription>
        )}
      </CardHeader>
      <form onSubmit={handleSubmit}>
        <CardContent className="space-y-4">
          {error && (
            <Alert variant="destructive">
              {error}
            </Alert>
          )}

          <div className="space-y-2">
            <Label>供應商名稱 *</Label>
            <Input
              name="name"
              value={formData.name}
              onChange={handleChange}
              disabled={isViewMode}
              placeholder="請輸入供應商名稱"
              required
            />
          </div>

          <div className="space-y-2">
            <Label>聯絡人</Label>
            <Input
              name="contactPerson"
              value={formData.contactPerson}
              onChange={handleChange}
              disabled={isViewMode}
              placeholder="請輸入聯絡人姓名"
            />
          </div>

          <div className="space-y-2">
            <Label>Email</Label>
            <Input
              name="email"
              type="email"
              value={formData.email}
              onChange={handleChange}
              disabled={isViewMode}
              placeholder="請輸入 Email"
            />
          </div>

          <div className="space-y-2">
            <Label>電話</Label>
            <Input
              name="phone"
              value={formData.phone}
              onChange={handleChange}
              disabled={isViewMode}
              placeholder="請輸入電話號碼"
            />
          </div>

          <div className="space-y-2">
            <Label>地址</Label>
            <Input
              name="address"
              value={formData.address}
              onChange={handleChange}
              disabled={isViewMode}
              placeholder="請輸入完整地址"
            />
          </div>
        </CardContent>

        <CardFooter className="flex gap-2">
          {isViewMode ? (
            <>
              <Link href={`/dashboard/erp/suppliers/${supplierId}/edit`} className="flex-1">
                <Button variant="outline" className="w-full">編輯</Button>
              </Link>
              <Button
                variant="outline"
                onClick={() => router.push('/dashboard/erp/suppliers')}
              >
                返回列表
              </Button>
            </>
          ) : (
            <>
              <Button type="submit" disabled={loading}>
                {loading ? '處理中...' : (mode === 'create' ? '建立' : '更新')}
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => router.push('/dashboard/erp/suppliers')}
              >
                取消
              </Button>
            </>
          )}
        </CardFooter>
      </form>
    </Card>
  )
}