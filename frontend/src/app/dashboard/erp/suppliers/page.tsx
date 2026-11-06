'use client'

import { useState } from 'react'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import SupplierService from '@/services/erp/supplier'
import { useAsyncData } from '@/hooks/useAsyncData'
import { NativeSelect } from '@/components/ui/native-select'
import { EmptyState, LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'
import { StatusBadge } from '@/components/ui/status-badge'
import { PageTitle } from '@/components/ui/page-title'

export default function SuppliersPage() {
  const [searchQuery, setSearchQuery] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('')

  const { data, error, loading, reload } = useAsyncData(
    () => SupplierService.listSuppliers(statusFilter || undefined),
    [statusFilter],
    { errorMessage: '載入供應商失敗' }
  )
  const suppliers = data ?? []

  const filteredSuppliers = suppliers.filter(s =>
    s.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    s.email?.toLowerCase().includes(searchQuery.toLowerCase())
  )

  if (loading) {
    return (
      <LoadingState />
    )
  }

  if (error) {
    return (
      <Alert variant="destructive">
        {error}
      </Alert>
    )
  }

  return (
    <div className="space-y-6">
      <PageTitle className="sr-only">供應商</PageTitle>
      <div className="flex flex-col sm:flex-row justify-between gap-4">
        <div className="flex-1 flex gap-4">
          <Input
            type="search"
            placeholder="搜尋供應商..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="max-w-xs"
          />
          <NativeSelect
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="w-auto"
          >
            <option value="">所有狀態</option>
            <option value="ACTIVE">啟用</option>
            <option value="INACTIVE">停用</option>
          </NativeSelect>
          <Button variant="outline" onClick={reload}>重新整理</Button>
        </div>
        <Link href="/dashboard/erp/suppliers/new">
          <Button>新增供應商</Button>
        </Link>
      </div>

      {filteredSuppliers.length === 0 ? (
        <Card>
          <EmptyState title="尚無供應商資料" action={<Link href="/dashboard/erp/suppliers/new">
              <Button>新增第一個供應商</Button>
            </Link>} className="h-48" />
        </Card>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredSuppliers.map((supplier) => (
            <Card key={supplier.id}>
              <CardHeader>
                <div className="flex justify-between items-start">
                  <CardTitle className="text-lg">{supplier.name}</CardTitle>
                  <StatusBadge domain="supplier" status={supplier.status} />
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