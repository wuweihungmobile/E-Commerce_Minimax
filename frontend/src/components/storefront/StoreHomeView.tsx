"use client"

import { StoreLayout } from "@/components/storefront/StoreLayout"
import { StoreHeroBanner } from "@/components/storefront/StoreHeroBanner"
import { StoreFeaturedListings } from "@/components/storefront/StoreFeaturedListings"

// 與 page.tsx 分離為獨立 client component：`StoreLayout` 的 `children` 是函式（需要
// `profile` 才能渲染 Hero），而 Next.js Server Component 不能把函式當 prop 傳給 Client
// Component（RSC 序列化限制）。page.tsx（server）只能傳純字串 `slug`，函式形式的
// children 必須在 client 元件之間建立（DEF-321(b)，Sprint 252 除錯後的修正）。
export function StoreHomeView({ slug }: { slug: string }) {
  return (
    <StoreLayout slug={slug} active="home">
      {(profile) => (
        <div className="flex flex-col gap-8">
          <StoreHeroBanner profile={profile} slug={slug} />
          <StoreFeaturedListings slug={slug} />
        </div>
      )}
    </StoreLayout>
  )
}
