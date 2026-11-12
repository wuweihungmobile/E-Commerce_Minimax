"use client"

import { ProductCard } from "@/components/storefront/ProductCard"
import { Pagination } from "@/components/ui/pagination"
import { Skeleton } from "@/components/ui/skeleton"
import storefrontService from "@/services/storefront"
import type { Listing, Page } from "@/services/listing"
import { useAsyncData } from "@/hooks/useAsyncData"
import { Button } from "@/components/ui/button"
import { LIST_PAGE_SIZE as PAGE_SIZE } from "@/lib/public-api"

export interface StoreListingGridProps {
  slug: string
  type: "PRODUCT" | "ROOM"
  /** 0-based 頁碼，由 server page 依 URL `page` 解析（DEF-020 慣例） */
  page: number
  /** 伺服器端已取好的這一頁（SEO 的伺服器端渲染，DEF-500）：有就直接用、不再請求；沒有（沒取到）就由瀏覽器自己載入 */
  initialListings?: Page<Listing>
}

// 店鋪前台「商品」／「房源」列表（PRD §10 /stores/[slug]/products、/rooms；
// DEF-321(b)，Sprint 252）。資料來源為公開端點 GET /v2/public/stores/{slug}/listings，
// 只回傳該店鋪 ACTIVE 上架項目，比照 HomeContent.tsx 的分頁與卡片呈現慣例。
export function StoreListingGrid({ slug, type, page, initialListings }: StoreListingGridProps) {
  const { data, error, reload } = useAsyncData(
    () => storefrontService.getStoreListings(slug, { type, page, size: PAGE_SIZE }),
    [slug, type, page],
    { errorMessage: "載入失敗，請稍後再試", initialData: initialListings }
  )

  // 分頁是真正的連結（DEF-500）：搜尋引擎與不執行 JavaScript 的爬蟲沿著它走到第 2 頁以後；第 1 頁不帶 page
  const hrefFor = (oneBased: number) => {
    const base = `/stores/${encodeURIComponent(slug)}/${type === "PRODUCT" ? "products" : "rooms"}`
    return oneBased > 1 ? `${base}?page=${oneBased}` : base
  }

  if (error) {
    return (
      <div
        data-testid="store-listing-error"
        className="flex flex-col items-center justify-center py-24 text-center gap-3"
      >
        <p className="text-lg text-rs-ink">載入失敗，請稍後再試</p>
        <Button
          variant="default"
          type="button"
          onClick={() => reload()}
        >
          重新載入
        </Button>
      </div>
    )
  }

  if (data === undefined) {
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
        <Pagination current={page + 1} total={data.totalPages} hrefFor={hrefFor} />
      )}
    </div>
  )
}
