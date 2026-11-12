"use client"

import { StoreLayout } from "@/components/storefront/StoreLayout"
import { StoreArticle } from "@/components/storefront/StoreArticle"
import type { ListingCardResponse, PostResponse } from "@/services/cms"
import type { StoreProfile } from "@/services/storefront"

// 與 StoreBlogView 同理：獨立 client component 以符合 RSC 函式 prop 限制（children 是函式）。
// 店鋪外殼載入公開店鋪資料（404＝店鋪不存在或非 ACTIVE，停權店鋪的文章因此也看不到），tenantId 由它提供給文章元件。
// initialProfile／initialPost／initialCards：伺服器端已取好的資料（SEO 的伺服器端渲染，DEF-500，Sprint 341）。
export function StoreArticleView({
  slug,
  postSlug,
  initialProfile,
  initialPost,
  initialCards,
}: {
  slug: string
  postSlug: string
  initialProfile?: StoreProfile
  initialPost?: PostResponse
  initialCards?: Record<string, ListingCardResponse>
}) {
  return (
    <StoreLayout slug={slug} active="blog" initialProfile={initialProfile}>
      {(profile) => (
        <StoreArticle
          storeSlug={slug}
          tenantId={profile.tenantId}
          postSlug={postSlug}
          initialPost={initialPost}
          initialCards={initialCards}
        />
      )}
    </StoreLayout>
  )
}
