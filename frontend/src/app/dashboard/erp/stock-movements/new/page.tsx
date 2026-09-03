'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import StockMovementService, {
  ManualStockMovementType,
  StockMovementRequest,
} from '@/services/erp/stockMovement'
import AuthService from '@/services/auth'

export default function NewStockMovementPage() {
  const router = useRouter()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [formData, setFormData] = useState<StockMovementRequest>({
    skuId: '',
    movementType: 'ADJUST_PLUS',
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

  // 只列後端手動異動 API 實際接受的型別（PRD §6.7.4 用語）。
  // DEF-063：這裡原本還有「入庫 INBOUND」與「出庫 OUTBOUND」，但那兩型是採購收貨與訂單出貨的
  // 系統流水帳，後端一直就禁止手動建立——連同當時前後端枚舉命名不一致，7 個選項有 5 個（含預設值
  // INBOUND）送出必定回 E_7005。手動增減庫存請用盤盈／盤虧調整。
  // 🔴 這份清單與後端 StockMovementService 的允許集合是一組耦合，見 MANUAL_MOVEMENT_TYPES 的註記。
  const movementTypes: { value: ManualStockMovementType; label: string }[] = [
    { value: 'ADJUST_PLUS', label: '盤盈調整 (+)' },
    { value: 'ADJUST_MINUS', label: '盤虧調整 (-)' },
    { value: 'TRANSFER_IN', label: '調撥入庫 (+)' },
    { value: 'TRANSFER_OUT', label: '調撥出庫 (-)' },
    { value: 'SCRAP', label: '報廢出庫 (-)' },
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
              <CardDescription>記錄盤點調整、調撥與報廢造成的庫存異動</CardDescription>
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
                    <p className="text-xs text-gray-500">
                      採購入庫與訂單出貨由採購單、訂單流程自動產生，不在此手動建立
                    </p>
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
                    <p className="text-xs text-gray-500">
                      店家自己記的單號；採購單／訂單產生的異動會自動顯示來源單據
                    </p>
                    <Input
                      name="referenceNumber"
                      value={formData.referenceNumber}
                      onChange={handleChange}
                      placeholder="選填，如：盤點單 2026-09"
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