"use client"

import { StoreLayout } from "@/components/storefront/StoreLayout"
import { StoreArticle } from "@/components/storefront/StoreArticle"

// 與 StoreBlogView 同理：獨立 client component 以符合 RSC 函式 prop 限制（children 是函式）。
// 店鋪外殼載入公開店鋪資料（404＝店鋪不存在或非 ACTIVE，停權店鋪的文章因此也看不到），tenantId 由它提供給文章元件。
export function StoreArticleView({ slug, postSlug }: { slug: string; postSlug: string }) {
  return (
    <StoreLayout slug={slug} active="blog">
      {(profile) => <StoreArticle storeSlug={slug} tenantId={profile.tenantId} postSlug={postSlug} />}
    </StoreLayout>
  )
}
