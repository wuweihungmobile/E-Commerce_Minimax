"use client"

import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import { ProductCard } from "@/components/storefront/ProductCard"
import { Pagination } from "@/components/ui/pagination"
import { Skeleton } from "@/components/ui/skeleton"
import storefrontService from "@/services/storefront"
import type { Listing, Page } from "@/services/listing"

const PAGE_SIZE = 12

export interface StoreListingGridProps {
  slug: string
  type: "PRODUCT" | "ROOM"
  /** 0-based 頁碼，由 server page 依 URL `page` 解析（DEF-020 慣例） */
  page: number
}

// 店鋪前台「商品」／「房源」列表（PRD §10 /stores/[slug]/products、/rooms；
// DEF-321(b)，Sprint 252）。資料來源為公開端點 GET /v2/public/stores/{slug}/listings，
// 只回傳該店鋪 ACTIVE 上架項目，比照 HomeContent.tsx 的分頁與卡片呈現慣例。
export function StoreListingGrid({ slug, type, page }: StoreListingGridProps) {
  const router = useRouter()
  const [data, setData] = useState<Page<Listing> | null>(null)
  const [error, setError] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    storefrontService
      .getStoreListings(slug, { type, page, size: PAGE_SIZE })
      .then((res) => {
        if (cancelled) return
        setData(res)
        setError(false)
      })
      .catch(() => {
        if (cancelled) return
        setData(null)
        setError(true)
      })
    return () => {
      cancelled = true
    }
  }, [slug, type, page, reloadKey])

  const handlePageChange = (oneBased: number) => {
    const params = new URLSearchParams()
    if (oneBased > 1) params.set("page", String(oneBased))
    const qs = params.toString()
    router.push(`/stores/${slug}/${type === "PRODUCT" ? "products" : "rooms"}${qs ? `?${qs}` : ""}`)
  }

  if (error) {
    return (
      <div
        data-testid="store-listing-error"
        className="flex flex-col items-center justify-center py-24 text-center gap-3"
      >
        <p className="text-lg text-rs-ink">載入失敗，請稍後再試</p>
        <button
          type="button"
          onClick={() => setReloadKey((k) => k + 1)}
          className="inline-flex items-center justify-center bg-rs-primary text-white rounded-md px-6 py-2.5 text-sm font-medium transition-colors hover:bg-rs-primary-hover"
        >
          重新載入
        </button>
      </div>
    )
  }

  if (data === null) {
    return (
      <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-6">
        {Array.from({ length: PAGE_SIZE }).map((_, i) => (
          <Skeleton key={i} className="aspect-square w-full rounded-lg" />
        ))}
      </div>
    )
  }

  if (data.content.length === 0) {
    return (
      <div
        data-testid="store-listing-empty"
        className="flex flex-col items-center justify-center py-24 text-center gap-2"
      >
        <p className="text-lg text-rs-ink">
          {type === "PRODUCT" ? "此店鋪目前沒有上架商品" : "此店鋪目前沒有上架房源"}
        </p>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <div
        data-testid="store-listing-grid"
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
            logistics={item.listingType === "ROOM" ? ["旅宿"] : ["賣家宅配"]}
          />
        ))}
      </div>
      {data.totalPages > 1 && (
        <Pagination current={page + 1} total={data.totalPages} onChange={handlePageChange} />
      )}
    </div>
  )
}
