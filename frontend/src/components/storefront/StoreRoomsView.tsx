"use client"

import { StoreLayout } from "@/components/storefront/StoreLayout"
import { StoreListingGrid } from "@/components/storefront/StoreListingGrid"

// 與 StoreHomeView 同理：獨立 client component 以符合 RSC 函式 prop 限制。
export function StoreRoomsView({ slug, page }: { slug: string; page: number }) {
  return (
    <StoreLayout slug={slug} active="rooms">
      {() => <StoreListingGrid key={page} slug={slug} type="ROOM" page={page} />}
    </StoreLayout>
  )
}
