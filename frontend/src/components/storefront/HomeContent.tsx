"use client"

import { useCallback, useEffect, useState } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { StorefrontShell } from "@/components/layout/StorefrontShell"
import { SidebarNav } from "@/components/storefront/SidebarNav"
import { SortToolbar } from "@/components/storefront/SortToolbar"
import { ProductCard } from "@/components/storefront/ProductCard"
import { Pagination } from "@/components/ui/pagination"
import { Skeleton } from "@/components/ui/skeleton"
import listingService, { type Listing, type Page } from "@/services/listing"

const CATEGORIES = [
  { id: "all", label: "全部商品" },
  { id: "product", label: "嚴選商品" },
  { id: "room", label: "旅宿房型" },
]

const SORT_TABS = [
  { id: "newest", label: "最新" },
  { id: "priceAsc", label: "價格低到高" },
  { id: "priceDesc", label: "價格高到低" },
]

const SORT_MAP: Record<string, { sortBy: string; sortDir: "ASC" | "DESC" }> = {
  newest: { sortBy: "createdAt", sortDir: "DESC" },
  priceAsc: { sortBy: "basePrice", sortDir: "ASC" },
  priceDesc: { sortBy: "basePrice", sortDir: "DESC" },
}

const PAGE_SIZE = 12

export interface HomeContentProps {
  /** 分類（all/product/room），由 server page 依 URL `type` 解析 */
  cat: string
  /** 排序頁籤（newest/priceAsc/priceDesc），由 server page 依 URL `sort` 解析 */
  sortTab: string
  /** 關鍵字，由 server page 依 URL `keyword` 解析 */
  keyword: string
  /** 0-based 頁碼，由 server page 依 URL `page` 解析 */
  page: number
}

// 首頁內容區（DEF-020：URL-driven 篩選）。篩選/排序/分頁改由 router.push 更新 URL query，
// server page 依 searchParams 重新以 props 傳入 → 本元件 effect 依 props 重新取資料
// （不再用 page-scoped state + nonce）。授權/錯誤三態分流沿用（401/403 引導、其他錯誤重試）。
export function HomeContent({ cat, sortTab, keyword, page }: HomeContentProps) {
  const router = useRouter()
  const [data, setData] = useState<Page<Listing> | null>(null)
  const [loading, setLoading] = useState(true)
  const [needsAuth, setNeedsAuth] = useState(false)
  const [error, setError] = useState(false)
  const [reloadKey, setReloadKey] = useState(0) // 僅供「重試同一查詢」用（非篩選變更）

  useEffect(() => {
    // 注意：不在 effect 內同步 setState（React 19 嚴格 hooks 禁令）。loading 初值為 true；
    // 篩選變更由 server page 以 key 觸發 remount（回到初值顯示 skeleton），retry 於 handler 設 loading。
    let cancelled = false
    const { sortBy, sortDir } = SORT_MAP[sortTab] ?? SORT_MAP.newest
    listingService
      .getListings({
        page,
        size: PAGE_SIZE,
        type: cat === "all" ? undefined : (cat as "product" | "room"),
        keyword: keyword || undefined,
        sortBy,
        sortDir,
      })
      .then((res) => {
        if (cancelled) return
        setData(res)
        setNeedsAuth(false)
        setError(false)
        setLoading(false)
      })
      .catch((err: unknown) => {
        if (cancelled) return
        const status =
          err && typeof err === "object" && "response" in err
            ? (err as { response?: { status?: number } }).response?.status
            : undefined
        if (status === 401 || status === 403) {
          setNeedsAuth(true)
          setError(false)
        } else {
          setNeedsAuth(false)
          setError(true)
        }
        setData(null)
        setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [cat, sortTab, keyword, page, reloadKey])

  // 更新 URL query（保留其他篩選）。篩選變更預設回第 1 頁。
  const navigate = useCallback(
    (next: { cat?: string; sort?: string; page?: number }) => {
      const c = next.cat ?? cat
      const s = next.sort ?? sortTab
      const p = next.page ?? 0
      const params = new URLSearchParams()
      if (c !== "all") params.set("type", c)
      if (s !== "newest") params.set("sort", s)
      if (keyword) params.set("keyword", keyword)
      if (p > 0) params.set("page", String(p + 1)) // URL 為 1-based
      const qs = params.toString()
      router.push(qs ? `/?${qs}` : "/")
    },
    [cat, sortTab, keyword, router]
  )

  const handleSelectCat = (id: string) => navigate({ cat: id, page: 0 })
  const handleSort = (id: string) => navigate({ sort: id, page: 0 })
  const handlePageChange = (oneBased: number) => navigate({ page: oneBased - 1 })
  const handleRetry = () => {
    // 事件處理器內 setState 合法（非 effect 同步）；顯示 skeleton 並重跑同一查詢
    setError(false)
    setLoading(true)
    setReloadKey((k) => k + 1)
  }

  return (
    <StorefrontShell
      sidebar={
        <SidebarNav items={CATEGORIES} active={cat} onSelect={handleSelectCat} />
      }
    >
      <SortToolbar
        tabs={SORT_TABS}
        activeTab={sortTab}
        onTabChange={handleSort}
        right={
          data ? (
            <span data-testid="result-count">共 {data.totalElements} 件</span>
          ) : null
        }
      />

      {loading ? (
        <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-6">
          {Array.from({ length: PAGE_SIZE }).map((_, i) => (
            <div key={i} className="flex flex-col gap-3">
              <Skeleton className="aspect-square w-full rounded-lg" />
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-5 w-1/3" />
            </div>
          ))}
        </div>
      ) : needsAuth ? (
        <div
          data-testid="home-auth-empty"
          className="flex flex-col items-center justify-center py-24 text-center gap-3"
        >
          <p className="text-lg text-rs-ink">登入後即可瀏覽精選商品</p>
          <p className="text-sm text-rs-ink-muted">
            意象若水 RUOSHUI｜生活減法，無負擔的購物體驗
          </p>
          <Link
            href="/login"
            className="mt-2 inline-flex items-center justify-center bg-rs-primary text-white rounded-md px-6 py-2.5 text-sm font-medium transition-colors hover:bg-rs-primary-hover"
          >
            前往登入
          </Link>
        </div>
      ) : error ? (
        <div
          data-testid="home-error"
          className="flex flex-col items-center justify-center py-24 text-center gap-3"
        >
          <p className="text-lg text-rs-ink">載入失敗，請稍後再試</p>
          <p className="text-sm text-rs-ink-muted">請確認網路連線後重試</p>
          <button
            type="button"
            onClick={handleRetry}
            className="mt-2 inline-flex items-center justify-center bg-rs-primary text-white rounded-md px-6 py-2.5 text-sm font-medium transition-colors hover:bg-rs-primary-hover"
          >
            重新載入
          </button>
        </div>
      ) : data && data.content.length > 0 ? (
        <div
          data-testid="product-grid"
          className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-6"
        >
          {data.content.map((item) => (
            <ProductCard
              key={item.id}
              href={`/reviews/product/${item.id}`}
              image={item.coverImageUrl}
              title={item.title}
              price={item.basePrice}
              currency={item.currency === "TWD" ? "NT$" : item.currency}
              features={item.tags?.slice(0, 2)}
              logistics={item.listingType === "ROOM" ? ["旅宿"] : ["賣家宅配"]}
            />
          ))}
        </div>
      ) : (
        <div
          data-testid="home-empty"
          className="flex flex-col items-center justify-center py-24 text-center gap-2"
        >
          <p className="text-lg text-rs-ink">目前沒有符合條件的商品</p>
          <p className="text-sm text-rs-ink-muted">換個分類或關鍵字再逛逛吧</p>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <Pagination
          current={page + 1}
          total={data.totalPages}
          onChange={handlePageChange}
        />
      )}
    </StorefrontShell>
  )
}
