"use client"

import { StoreLayout } from "@/components/storefront/StoreLayout"
import { StoreBlogList } from "@/components/storefront/StoreBlogList"
import type { CategoryListResponse, PostListResponse } from "@/services/cms"
import type { StoreProfile } from "@/services/storefront"

// 與 StoreHomeView 同理：獨立 client component 以符合 RSC 函式 prop 限制。
//
// 列表以「頁碼＋分類」為 key（Sprint 341，DEF-500）：換頁、換分類時重新掛載，直接用 server page 傳來的新一頁資料，
// 不再多打一次請求。Sprint 337（DEF-486）當時拿掉 key，是因為列表自己管資料狀態、重新掛載會讓篩選列消失再重抓而閃一下；
// 現在分類清單也由伺服器端帶來（initialCategories），重新掛載的第一個畫面就有篩選列與貼文，不會閃。
export function StoreBlogView({
  slug,
  page,
  categoryId,
  initialProfile,
  initialCategories,
  initialPosts,
}: {
  slug: string
  page: number
  categoryId?: string
  initialProfile?: StoreProfile
  initialCategories?: CategoryListResponse
  initialPosts?: PostListResponse
}) {
  return (
    <StoreLayout slug={slug} active="blog" initialProfile={initialProfile}>
      {(profile) => (
        <StoreBlogList
          key={`${page}|${categoryId ?? ""}`}
          slug={slug}
          tenantId={profile.tenantId}
          page={page}
          categoryId={categoryId}
          initialCategories={initialCategories}
          initialPosts={initialPosts}
        />
      )}
    </StoreLayout>
  )
}
