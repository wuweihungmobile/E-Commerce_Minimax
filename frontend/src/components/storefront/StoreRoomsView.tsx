"use client"

import { StoreLayout } from "@/components/storefront/StoreLayout"
import { StoreListingGrid } from "@/components/storefront/StoreListingGrid"
import type { StoreProfile } from "@/services/storefront"
import type { Listing, Page } from "@/services/listing"

// 與 StoreHomeView 同理：獨立 client component 以符合 RSC 函式 prop 限制。
// initialProfile／initialListings：伺服器端已取好的資料（SEO 的伺服器端渲染，DEF-500，Sprint 341）。
export function StoreRoomsView({
  slug,
  page,
  initialProfile,
  initialListings,
}: {
  slug: string
  page: number
  initialProfile?: StoreProfile
  initialListings?: Page<Listing>
}) {
  return (
    <StoreLayout slug={slug} active="rooms" initialProfile={initialProfile}>
      {() => <StoreListingGrid key={page} slug={slug} type="ROOM" page={page} initialListings={initialListings} />}
    </StoreLayout>
  )
}
