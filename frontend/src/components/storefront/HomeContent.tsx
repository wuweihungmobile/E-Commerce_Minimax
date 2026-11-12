"use client"

import { useCallback } from "react"
import { useRouter } from "next/navigation"
import { StorefrontShell } from "@/components/layout/StorefrontShell"
import { SidebarNav } from "@/components/storefront/SidebarNav"
import { SortToolbar } from "@/components/storefront/SortToolbar"
import { ProductCard } from "@/components/storefront/ProductCard"
import { Pagination } from "@/components/ui/pagination"
import { Skeleton } from "@/components/ui/skeleton"
import listingService, { type Listing, type Page } from "@/services/listing"
import { useAsyncData } from "@/hooks/useAsyncData"
import { Button } from "@/components/ui/button"
import { PageTitle } from '@/components/ui/page-title'
import { LIST_PAGE_SIZE as PAGE_SIZE, SORT_MAP } from "@/lib/public-api"

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

/** 首頁網址：篩選條件都在 query 裡（只帶非預設值），第 1 頁不帶 page；page 是 0-based，網址是 1-based。 */
function homeHref(cat: string, sortTab: string, keyword: string, page: number): string {
  const params = new URLSearchParams()
  if (cat !== "all") params.set("type", cat)
  if (sortTab !== "newest") params.set("sort", sortTab)
  if (keyword) params.set("keyword", keyword)
  if (page > 0) params.set("page", String(page + 1)) // URL 為 1-based
  const qs = params.toString()
  return qs ? `/?${qs}` : "/"
}

export interface HomeContentProps {
  /** 分類（all/product/room），由 server page 依 URL `type` 解析 */
  cat: string
  /** 排序頁籤（newest/priceAsc/priceDesc），由 server page 依 URL `sort` 解析 */
  sortTab: string
  /** 關鍵字，由 server page 依 URL `keyword` 解析 */
  keyword: string
  /** 0-based 頁碼，由 server page 依 URL `page` 解析 */
  page: number
  /** 伺服器端已取好的這一頁（SEO 的伺服器端渲染，DEF-500）：有就直接用、不再請求；沒有（沒取到）就由瀏覽器自己載入 */
  initialResult?: Page<Listing>
}

// 首頁內容區（DEF-020：URL-driven 篩選）。篩選/排序/分頁改由 router.push 更新 URL query，
// server page 依 searchParams 重新以 props 傳入 → 本元件 effect 依 props 重新取資料
// （不再用 page-scoped state + nonce）。
//
// DEF-358(a)（Sprint 259）：改呼叫跨店鋪公開端點 getPublicListings（匿名可用，比照 PRD §12.7
// 「Guest 可瀏覽所有商品與房源」），取代先前需登入的 getListings——先前 401/403 時顯示
// 「登入後即可瀏覽精選商品」的 needsAuth 分支因此移除，不再是必要狀態。
//
// SEO（DEF-500，Sprint 341）：首頁是整站最重要的入口。server page 先取好這一頁的商品／房源以 initialResult 傳進來，
// 第一份 HTML 就有商品卡片與連結；分頁是真正的連結，搜尋引擎能沿著它走到第 2 頁以後。
// 分類、排序仍是按鈕：分類對應的列表有專屬頁面（/products、/rooms，各自可被收錄），排序只是同一批資料的另一種排列。
export function HomeContent({ cat, sortTab, keyword, page, initialResult }: HomeContentProps) {
  const router = useRouter()
  const { data: result, error, loading, reload } = useAsyncData(
    () => {
      const { sortBy, sortDir } = SORT_MAP[sortTab] ?? SORT_MAP.newest
      return listingService.getPublicListings({
        page,
        size: PAGE_SIZE,
        type: cat === "all" ? undefined : (cat as "product" | "room"),
        keyword: keyword || undefined,
        sortBy,
        sortDir,
      })
    },
    [cat, sortTab, keyword, page],
    { errorMessage: "載入失敗，請稍後再試", initialData: initialResult }
  )
  // 失敗時不顯示舊資料（與原本失敗後清成 null 相同）
  const data = error === null ? result : undefined

  // 更新 URL query（保留其他篩選）。篩選變更預設回第 1 頁。
  const navigate = useCallback(
    (next: { cat?: string; sort?: string; page?: number }) => {
      router.push(homeHref(next.cat ?? cat, next.sort ?? sortTab, keyword, next.page ?? 0))
    },
    [cat, sortTab, keyword, router]
  )

  const handleSelectCat = (id: string) => navigate({ cat: id, page: 0 })
  const handleSort = (id: string) => navigate({ sort: id, page: 0 })
  const handleRetry = () => {
    reload()
  }

  return (
    <StorefrontShell
      sidebar={
        <SidebarNav items={CATEGORIES} active={cat} onSelect={handleSelectCat} />
      }
    >
      <PageTitle className="sr-only">意象若水 RUOSHUI</PageTitle>
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
      ) : error ? (
        <div
          data-testid="home-error"
          className="flex flex-col items-center justify-center py-24 text-center gap-3"
        >
          <p className="text-lg text-rs-ink">載入失敗，請稍後再試</p>
          <p className="text-sm text-rs-ink-muted">請確認網路連線後重試</p>
          <Button
            variant="default"
            type="button"
            onClick={handleRetry}
            className="mt-2"
          >
            重新載入
          </Button>
        </div>
      ) : data && data.content.length > 0 ? (
        <div
          data-testid="product-grid"
          className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-6"
        >
          {data.content.map((item) => (
            <ProductCard
              key={item.id}
              href={`/listings/${item.id}`}
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
          hrefFor={(oneBased) => homeHref(cat, sortTab, keyword, oneBased - 1)}
        />
      )}
    </StorefrontShell>
  )
}
