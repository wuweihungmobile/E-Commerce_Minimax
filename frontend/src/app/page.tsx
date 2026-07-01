"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
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

const HOT_KEYWORDS = ["極簡生活", "質感家居", "旅宿房型", "香氛療癒", "收納"]
const PAGE_SIZE = 12

export default function HomePage() {
  const [cat, setCat] = useState("all")
  const [sortTab, setSortTab] = useState("newest")
  const [keyword, setKeyword] = useState("")
  const [page, setPage] = useState(0) // 0-based（API）
  const [nonce, setNonce] = useState(0) // 強制重跑查詢（避免同值操作卡 loading）
  const [data, setData] = useState<Page<Listing> | null>(null)
  const [loading, setLoading] = useState(true)
  const [needsAuth, setNeedsAuth] = useState(false)
  const [error, setError] = useState(false)
  const [cartCount, setCartCount] = useState(0)

  useEffect(() => {
    let cancelled = false
    const { sortBy, sortDir } = SORT_MAP[sortTab]
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
  }, [cat, sortTab, keyword, page, nonce])

  useEffect(() => {
    let cancelled = false
    if (typeof window !== "undefined" && localStorage.getItem("accessToken")) {
      listingService
        .getCartCount()
        .then((c) => {
          if (!cancelled) setCartCount(c)
        })
        .catch(() => {
          // 購物車數量取得失敗不影響首頁
        })
    }
    return () => {
      cancelled = true
    }
  }, [])

  const handleSelectCat = (id: string) => {
    setLoading(true)
    setCat(id)
    setPage(0)
    setNonce((n) => n + 1)
  }
  const handleSort = (id: string) => {
    setLoading(true)
    setSortTab(id)
    setPage(0)
    setNonce((n) => n + 1)
  }
  const handleSearch = (q: string) => {
    setLoading(true)
    setKeyword(q)
    setPage(0)
    setNonce((n) => n + 1)
  }
  const handlePageChange = (oneBased: number) => {
    setLoading(true)
    setPage(oneBased - 1)
    setNonce((n) => n + 1)
  }
  const handleRetry = () => {
    setLoading(true)
    setError(false)
    setNonce((n) => n + 1)
  }

  return (
    <StorefrontShell
      sidebar={
        <SidebarNav items={CATEGORIES} active={cat} onSelect={handleSelectCat} />
      }
      cartCount={cartCount}
      hotKeywords={HOT_KEYWORDS}
      onSearch={handleSearch}
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
