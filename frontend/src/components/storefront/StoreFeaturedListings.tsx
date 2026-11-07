"use client"

import { ProductCard } from "@/components/storefront/ProductCard"
import { ErrorState } from "@/components/ui/states"
import { Skeleton } from "@/components/ui/skeleton"
import storefrontService from "@/services/storefront"
import { useAsyncData } from "@/hooks/useAsyncData"

const FEATURED_LIMIT = 8

// 店鋪前台首頁「精選商品/房源」區塊（PRD §11.3.1 <StoreFeaturedListings/>；PRD §12.7；
// DEF-321(b)，Sprint 252）。StoreOwner 於商品/房源編輯表單勾選「精選」後顯示於此。
export function StoreFeaturedListings({ slug }: { slug: string }) {
  const { data: featured, error, reload } = useAsyncData(
    () => storefrontService.getStoreListings(slug, { featured: true, size: FEATURED_LIMIT }),
    [slug],
    { errorMessage: "載入精選商品失敗" }
  )

  // 載入失敗要讓使用者看得出來並能重試（DEF-468：原本當成「沒有精選」而整塊消失，分不出是店主沒設定還是載入壞了）。
  // 「成功但沒有精選項目」仍然整塊不顯示（下面的 length === 0）：那是店主沒勾選，不是出錯。
  if (error !== null) {
    return (
      <ErrorState
        data-testid="store-featured-error"
        title="精選商品/房源載入失敗"
        onRetry={reload}
        className="py-6"
      />
    )
  }

  // undefined＝還在載入
  const listings = featured?.content

  if (listings === undefined) {
    return (
      <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="aspect-square w-full rounded-lg" />
        ))}
      </div>
    )
  }

  if (listings.length === 0) {
    return null
  }

  return (
    <div data-testid="store-featured-listings" className="flex flex-col gap-4">
      <h3 className="text-lg font-semibold text-rs-ink">精選商品/房源</h3>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
        {listings.map((item) => (
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
    </div>
  )
}
