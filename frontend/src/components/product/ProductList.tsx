'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import ProductService, { ProductListItem, ProductFilters } from '@/services/product'
import AuthService from '@/services/auth'

export default function ProductList() {
  const router = useRouter()
  const [products, setProducts] = useState<ProductListItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [category, setCategory] = useState<string>('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [deleteLoading, setDeleteLoading] = useState<string | null>(null)

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
    fetchProducts()
  }, [router, category, page])

  const fetchProducts = async () => {
    setLoading(true)
    setError(null)

    try {
      const filters: ProductFilters = {
        keyword: searchQuery || undefined,
        category: category || undefined,
        page,
        size: 20,
        sortBy: 'createdAt',
        sortDir: 'DESC',
      }
      const response = await ProductService.getProducts(filters)
      setProducts(response.content)
      setTotalPages(response.totalPages)
      setTotalElements(response.totalElements)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || '載入商品失敗')
      } else {
        setError('載入商品失敗')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleSearch = () => {
    setPage(0)
    fetchProducts()
  }

  const handleDelete = async (id: string) => {
    if (!confirm('確定要刪除這個商品嗎？')) return

    setDeleteLoading(id)
    try {
      await ProductService.deleteProduct(id)
      fetchProducts()
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

  const getStatusBadge = (status: string) => {
    const statusConfig: Record<string, { variant: 'default' | 'secondary' | 'destructive' | 'success' | 'outline'; label: string }> = {
      ACTIVE: { variant: 'success', label: '上架中' },
      INACTIVE: { variant: 'secondary', label: '已下架' },
      DRAFT: { variant: 'outline', label: '草稿' },
    }
    const config = statusConfig[status] || { variant: 'outline', label: status }
    return <Badge variant={config.variant}>{config.label}</Badge>
  }

  const formatPrice = (price: number, currency: string) => {
    return new Intl.NumberFormat('zh-TW', {
      style: 'currency',
      currency: currency || 'TWD',
    }).format(price)
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
            placeholder="搜尋商品..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
            className="max-w-xs"
          />
          <Select value={category} onValueChange={setCategory}>
            <SelectTrigger className="w-40">
              <SelectValue placeholder="所有類別" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="">所有類別</SelectItem>
              <SelectItem value="ELECTRONICS">電子產品</SelectItem>
              <SelectItem value="FASHION">時尚服飾</SelectItem>
              <SelectItem value="HOME">居家生活</SelectItem>
              <SelectItem value="FOOD">食品飲料</SelectItem>
              <SelectItem value="BEAUTY">美妝保養</SelectItem>
              <SelectItem value="SPORTS">運動休閒</SelectItem>
              <SelectItem value="BOOKS">圖書文具</SelectItem>
              <SelectItem value="OTHER">其他</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" onClick={handleSearch}>
            搜尋
          </Button>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => fetchProducts()}>
            重新整理
          </Button>
          <Link href="/dashboard/products/new">
            <Button>新增商品</Button>
          </Link>
        </div>
      </div>

      {/* Product List */}
      {products.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center h-48">
            <p className="text-gray-500 mb-4">尚無商品資料</p>
            <Link href="/dashboard/products/new">
              <Button>新增第一個商品</Button>
            </Link>
          </CardContent>
        </Card>
      ) : (
        <>
          <div className="text-sm text-gray-600">
            共 {totalElements} 筆資料，第 {page + 1} / {totalPages} 頁
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            {products.map((product) => (
              <Card key={product.listingId} className="relative">
                <CardHeader>
                  <div className="flex justify-between items-start">
                    <CardTitle className="text-lg line-clamp-1">{product.title}</CardTitle>
                    {getStatusBadge(product.status)}
                  </div>
                  <CardDescription>{product.category}</CardDescription>
                </CardHeader>
                <CardContent className="space-y-2">
                  {product.coverImageUrl && (
                    <img
                      src={product.coverImageUrl}
                      alt={product.title}
                      className="w-full h-32 object-cover rounded-md"
                    />
                  )}
                  <div className="text-sm">
                    <span className="text-muted-foreground">品牌：</span>
                    {product.brand || '-'}
                  </div>
                  <div className="text-lg font-bold text-primary">
                    {formatPrice(product.basePrice, product.currency)}
                  </div>
                  <div className="text-xs text-muted-foreground">
                    建立時間：{new Date(product.createdAt).toLocaleDateString('zh-TW')}
                  </div>
                </CardContent>
                <CardFooter className="flex gap-2">
                  <Link href={`/dashboard/products/${product.listingId}/edit`} className="flex-1">
                    <Button variant="outline" className="w-full">
                      編輯
                    </Button>
                  </Link>
                  <Button
                    variant="destructive"
                    size="sm"
                    onClick={() => handleDelete(product.listingId)}
                    disabled={deleteLoading === product.listingId}
                  >
                    {deleteLoading === product.listingId ? '刪除中...' : '刪除'}
                  </Button>
                </CardFooter>
              </Card>
            ))}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex justify-center gap-2 mt-6">
              <Button
                variant="outline"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
              >
                上一頁
              </Button>
              <span className="flex items-center px-4">
                第 {page + 1} 頁，共 {totalPages} 頁
              </span>
              <Button
                variant="outline"
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
              >
                下一頁
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  )
}