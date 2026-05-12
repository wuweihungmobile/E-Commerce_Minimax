'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import SupplierService, {
  SupplierDto,
  SupplierCreateRequest,
  SupplierUpdateRequest,
} from '@/services/erp/supplier'
import AuthService from '@/services/auth'

interface SupplierFormProps {
  supplierId?: string
  mode: 'create' | 'edit' | 'view'
}

export default function SupplierForm({ supplierId, mode }: SupplierFormProps) {
  const router = useRouter()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [initialLoading, setInitialLoading] = useState(supplierId ? true : false)

  const [formData, setFormData] = useState<SupplierCreateRequest>({
    name: '',
    contactPerson: '',
    email: '',
    phone: '',
    address: '',
  })

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }

    if (supplierId) {
      fetchSupplier()
    }
  }, [router, supplierId])

  const fetchSupplier = async () => {
    if (!supplierId) return
    setInitialLoading(true)
    try {
      const data = await SupplierService.getSupplier(supplierId)
      setFormData({
        name: data.name,
        contactPerson: data.contactPerson || '',
        email: data.email || '',
        phone: data.phone || '',
        address: data.address || '',
      })
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '載入供應商失敗')
      } else {
        setError('載入供應商失敗')
      }
    } finally {
      setInitialLoading(false)
    }
  }

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target
    setFormData(prev => ({ ...prev, [name]: value }))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!formData.name.trim()) {
      alert('請輸入供應商名稱')
      return
    }

    setLoading(true)
    setError(null)

    try {
      if (mode === 'create') {
        await SupplierService.createSupplier(formData)
        alert('供應商建立成功')
        router.push('/dashboard/erp/suppliers')
      } else if (supplierId) {
        await SupplierService.updateSupplier(supplierId, formData)
        alert('供應商更新成功')
        router.push('/dashboard/erp/suppliers')
      }
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '操作失敗')
      } else {
        setError('操作失敗')
      }
    } finally {
      setLoading(false)
    }
  }

  const isViewMode = mode === 'view'

  if (initialLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">載入中...</div>
      </div>
    )
  }

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
            <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">
              {error}
            </div>
          )}

          <div className="space-y-2">
            <label className="text-sm font-medium">供應商名稱 *</label>
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
            <label className="text-sm font-medium">聯絡人</label>
            <Input
              name="contactPerson"
              value={formData.contactPerson}
              onChange={handleChange}
              disabled={isViewMode}
              placeholder="請輸入聯絡人姓名"
            />
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium">Email</label>
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
            <label className="text-sm font-medium">電話</label>
            <Input
              name="phone"
              value={formData.phone}
              onChange={handleChange}
              disabled={isViewMode}
              placeholder="請輸入電話號碼"
            />
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium">地址</label>
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