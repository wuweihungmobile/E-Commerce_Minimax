'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import SupplierService, { SupplierDto } from '@/services/erp/supplier'
import AuthService from '@/services/auth'

export default function SuppliersPage() {
  const router = useRouter()
  const [suppliers, setSuppliers] = useState<SupplierDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('')

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
    fetchSuppliers()
  }, [router, statusFilter])

  const fetchSuppliers = async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await SupplierService.listSuppliers(statusFilter || undefined)
      setSuppliers(data)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '載入供應商失敗')
      } else {
        setError('載入供應商失敗')
      }
    } finally {
      setLoading(false)
    }
  }

  const filteredSuppliers = suppliers.filter(s =>
    s.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    s.email?.toLowerCase().includes(searchQuery.toLowerCase())
  )

  const getStatusBadge = (status: string) => {
    const config: Record<string, { variant: 'default' | 'secondary' | 'destructive' | 'success' | 'outline'; label: string }> = {
      ACTIVE: { variant: 'success', label: '啟用' },
      INACTIVE: { variant: 'secondary', label: '停用' },
    }
    const c = config[status] || { variant: 'outline', label: status }
    return <Badge variant={c.variant}>{c.label}</Badge>
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
        <div className="flex-1 flex gap-4">
          <Input
            type="search"
            placeholder="搜尋供應商..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="max-w-xs"
          />
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="border rounded-md px-3 py-2"
          >
            <option value="">所有狀態</option>
            <option value="ACTIVE">啟用</option>
            <option value="INACTIVE">停用</option>
          </select>
          <Button variant="outline" onClick={fetchSuppliers}>重新整理</Button>
        </div>
        <Link href="/dashboard/erp/suppliers/new">
          <Button>新增供應商</Button>
        </Link>
      </div>

      {filteredSuppliers.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center h-48">
            <p className="text-gray-500 mb-4">尚無供應商資料</p>
            <Link href="/dashboard/erp/suppliers/new">
              <Button>新增第一個供應商</Button>
            </Link>
          </CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredSuppliers.map((supplier) => (
            <Card key={supplier.id}>
              <CardHeader>
                <div className="flex justify-between items-start">
                  <CardTitle className="text-lg">{supplier.name}</CardTitle>
                  {getStatusBadge(supplier.status)}
                </div>
                <CardDescription>{supplier.contactPerson || '-'}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-2 text-sm">
                <div className="flex items-center gap-2">
                  <span className="text-muted-foreground">Email：</span>
                  <span>{supplier.email || '-'}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-muted-foreground">電話：</span>
                  <span>{supplier.phone || '-'}</span>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-muted-foreground">地址：</span>
                  <span className="truncate">{supplier.address || '-'}</span>
                </div>
              </CardContent>
              <CardFooter className="flex gap-2">
                <Link href={`/dashboard/erp/suppliers/${supplier.id}`} className="flex-1">
                  <Button variant="outline" className="w-full">檢視</Button>
                </Link>
                <Link href={`/dashboard/erp/suppliers/${supplier.id}/edit`} className="flex-1">
                  <Button variant="outline" className="w-full">編輯</Button>
                </Link>
              </CardFooter>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}