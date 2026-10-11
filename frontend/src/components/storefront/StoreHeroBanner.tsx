import Link from "next/link"
import type { StoreProfile } from "@/services/storefront"

// 店鋪前台首頁 Hero 區塊（PRD §11.3.1 <StoreHeroBanner/>；DEF-321(b)，Sprint 252）。
//
// 範圍縮減（見 SPRINT_252_PLAN.md §2.1）：PRD 定義的 bannerImageUrl/ctaText 目前無對應欄位
// 也無策展 UI，本輪不新增專屬橫幅圖片，直接用既有 logoUrl/name/description；CTA 固定提供
// 「商品」「房源」兩個入口（而非依資料猜一個），讓訪客自行選擇，不需額外查詢去判斷店鋪
// 主要經營哪一種。
export function StoreHeroBanner({ profile, slug }: { profile: StoreProfile; slug: string }) {
  return (
    <div
      data-testid="store-hero-banner"
      className="flex flex-col gap-4 rounded-lg border border-rs-hairline bg-rs-tertiary/40 p-8"
    >
      {profile.logoUrl ? (
        // eslint-disable-next-line @next/next/no-img-element -- 店鋪 Logo 為後端動態 URL
        <img
          src={profile.logoUrl}
          alt={profile.name}
          className="w-16 h-16 rounded-full object-cover border border-rs-hairline"
        />
      ) : null}
      <h2 className="text-2xl font-semibold text-rs-ink">{profile.name}</h2>
      {profile.description && (
        <p className="text-sm text-rs-ink-muted whitespace-pre-line">{profile.description}</p>
      )}
      <div className="flex gap-3">
        <Link
          href={`/stores/${slug}/products`}
          className="inline-flex items-center justify-center bg-rs-primary text-white rounded-md px-6 py-2.5 text-sm font-medium transition-colors hover:bg-rs-primary-hover"
        >
          查看商品
        </Link>
        <Link
          href={`/stores/${slug}/rooms`}
          className="inline-flex items-center justify-center border border-rs-primary text-rs-primary rounded-md px-6 py-2.5 text-sm font-medium transition-colors hover:bg-rs-primary hover:text-white"
        >
          查看房源
        </Link>
      </div>
    </div>
  )
}
