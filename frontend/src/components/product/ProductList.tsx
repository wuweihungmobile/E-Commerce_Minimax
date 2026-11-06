'use client'

import { useState } from 'react'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import ProductService, { ProductFilters } from '@/services/product'
import { EmptyState, LoadingState } from '@/components/ui/states'
import { Alert } from '@/components/ui/alert'
import { confirmDialog } from '@/components/ui/confirm-dialog'
import { toast } from '@/components/ui/toast'
import { StatusBadge } from '@/components/ui/status-badge'
import { apiErrorMessage } from '@/lib/api-error'
import { useAsyncData } from '@/hooks/useAsyncData'

// Radix 的 Select.Item 不接受空字串 value（render 時直接 throw，整頁崩潰）；「所有類別」用哨兵值代表，
// category 狀態與送出的篩選參數仍以空字串代表不篩選。
const ALL_CATEGORIES = 'ALL'

export default function ProductList() {
  const [searchQuery, setSearchQuery] = useState('')
  // 搜尋框的內容與「已套用的搜尋」分開：只在按 Enter／搜尋鈕時套用，輸入時不重抓；
  // 每次套用都是新物件，所以同樣的關鍵字再按一次也會重抓（等同重新整理）
  const [applied, setApplied] = useState({ keyword: '' })
  const [category, setCategory] = useState<string>('')
  const [page, setPage] = useState(0)
  const [deleteLoading, setDeleteLoading] = useState<string | null>(null)

  const { data, error, loading, reload } = useAsyncData(
    () => {
      const filters: ProductFilters = {
        keyword: applied.keyword || undefined,
        category: category || undefined,
        page,
        size: 20,
        sortBy: 'createdAt',
        sortDir: 'DESC',
      }
      return ProductService.getProducts(filters)
    },
    [applied, category, page],
    { errorMessage: '載入商品失敗' }
  )
  const products = data?.content ?? []
  const totalPages = data?.totalPages ?? 0
  const totalElements = data?.totalElements ?? 0

  const handleSearch = () => {
    setApplied({ keyword: searchQuery })
    setPage(0)
  }

  const handleDelete = async (id: string) => {
    if (!(await confirmDialog({ message: '確定要刪除這個商品嗎？', confirmLabel: '刪除', destructive: true }))) return

    setDeleteLoading(id)
    try {
      await ProductService.deleteProduct(id)
      reload()
    } catch (err: unknown) {
      toast.error(apiErrorMessage(err, '刪除失敗'))
    } finally {
      setDeleteLoading(null)
    }
  }

  const formatPrice = (price: number, currency: string) => {
    return new Intl.NumberFormat('zh-TW', {
      style: 'currency',
      currency: currency || 'TWD',
    }).format(price)
  }

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
          <Select
            value={category || ALL_CATEGORIES}
            onValueChange={(value) => setCategory(value === ALL_CATEGORIES ? '' : value)}
          >
            <SelectTrigger className="w-40">
              <SelectValue placeholder="所有類別" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_CATEGORIES}>所有類別</SelectItem>
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
          <Button variant="outline" onClick={reload}>
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
          <EmptyState title="尚無商品資料" action={<Link href="/dashboard/products/new">
              <Button>新增第一個商品</Button>
            </Link>} className="h-48" />
        </Card>
      ) : (
        <>
          <div className="text-sm text-rs-ink-muted">
            共 {totalElements} 筆資料，第 {page + 1} / {totalPages} 頁
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            {products.map((product) => (
              <Card key={product.listingId} className="relative">
                <CardHeader>
                  <div className="flex justify-between items-start">
                    <CardTitle className="text-lg line-clamp-1">{product.title}</CardTitle>
                    <StatusBadge domain="listing" status={product.status} />
                  </div>
                  <CardDescription>{product.category}</CardDescription>
                </CardHeader>
                <CardContent className="space-y-2">
                  {product.coverImageUrl && (
                    // eslint-disable-next-line @next/next/no-img-element -- 封面／精選圖是店家輸入的任意網址；next/image 需要 width／height，且網域須事先在 next.config 設定 remotePatterns
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