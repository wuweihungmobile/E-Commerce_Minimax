'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import StockMovementService, {
  StockMovementRequest,
  StockMovementType,
} from '@/services/erp/stockMovement'
import AuthService from '@/services/auth'

export default function NewStockMovementPage() {
  const router = useRouter()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [formData, setFormData] = useState<StockMovementRequest>({
    skuId: '',
    movementType: 'INBOUND',
    quantity: 1,
    referenceNumber: '',
    notes: '',
  })

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
  }, [router])

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target
    setFormData(prev => ({
      ...prev,
      [name]: name === 'quantity' ? parseInt(value) || 0 : value,
    }))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    if (!formData.skuId.trim()) {
      alert('請輸入 SKU ID')
      return
    }

    if (formData.quantity <= 0) {
      alert('數量必須大於 0')
      return
    }

    setLoading(true)
    setError(null)

    try {
      await StockMovementService.createStockMovement(formData)
      alert('庫存異動建立成功')
      router.push('/dashboard/erp/stock-movements')
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

  const movementTypes: { value: StockMovementType; label: string }[] = [
    { value: 'INBOUND', label: '入庫' },
    { value: 'OUTBOUND', label: '出庫' },
    { value: 'ADJUST_PLUS', label: '調整(+)' },
    { value: 'ADJUST_MINUS', label: '調整(-)' },
    { value: 'TRANSFER_IN', label: '轉入' },
    { value: 'TRANSFER_OUT', label: '轉出' },
    { value: 'SCRAP', label: '報廢' },
  ]

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center gap-4">
              <Link href="/dashboard" className="text-gray-600 hover:text-gray-900">
                Dashboard
              </Link>
              <span className="text-gray-400">/</span>
              <Link href="/dashboard/erp/stock-movements" className="text-gray-600 hover:text-gray-900">
                庫存異動
              </Link>
              <span className="text-gray-400">/</span>
              <span className="text-gray-900 font-medium">新增</span>
            </div>
            <div className="flex items-center gap-4">
              <span className="text-sm text-gray-600">
                {AuthService.getCurrentUser()?.email}
              </span>
              <button
                onClick={() => {
                  AuthService.clearAuthData()
                  router.push('/login')
                }}
                className="px-3 py-1.5 text-sm text-white bg-red-500 rounded-md hover:bg-red-600"
              >
                登出
              </button>
            </div>
          </div>
        </div>
      </nav>

      <main className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="px-4 py-6 sm:px-0">
          <div className="mb-6">
            <h1 className="text-2xl font-bold text-gray-900">新增庫存異動</h1>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>填寫異動資料</CardTitle>
              <CardDescription>記錄庫存的入庫、出庫或調整異動</CardDescription>
            </CardHeader>
            <form onSubmit={handleSubmit}>
              <CardContent className="space-y-4">
                {error && (
                  <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">
                    {error}
                  </div>
                )}

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <label className="text-sm font-medium">SKU ID *</label>
                    <Input
                      name="skuId"
                      value={formData.skuId}
                      onChange={handleChange}
                      placeholder="請輸入 SKU ID"
                      required
                    />
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-medium">異動類型 *</label>
                    <select
                      name="movementType"
                      value={formData.movementType}
                      onChange={handleChange}
                      className="w-full border rounded-md px-3 py-2"
                    >
                      {movementTypes.map(type => (
                        <option key={type.value} value={type.value}>
                          {type.label}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <label className="text-sm font-medium">數量 *</label>
                    <Input
                      name="quantity"
                      type="number"
                      min="1"
                      value={formData.quantity}
                      onChange={handleChange}
                      required
                    />
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-medium">參考單號</label>
                    <Input
                      name="referenceNumber"
                      value={formData.referenceNumber}
                      onChange={handleChange}
                      placeholder="選填，如：PO-2024-001"
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <label className="text-sm font-medium">備註</label>
                  <textarea
                    name="notes"
                    value={formData.notes}
                    onChange={handleChange}
                    placeholder="選填，補充說明..."
                    rows={3}
                    className="w-full border rounded-md px-3 py-2"
                  />
                </div>
              </CardContent>

              <CardFooter className="flex gap-2">
                <Button type="submit" disabled={loading}>
                  {loading ? '建立中...' : '建立異動'}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => router.push('/dashboard/erp/stock-movements')}
                >
                  取消
                </Button>
              </CardFooter>
            </form>
          </Card>
        </div>
      </main>
    </div>
  )
}