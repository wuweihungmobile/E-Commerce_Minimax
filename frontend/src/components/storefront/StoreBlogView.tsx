"use client"

import { StoreLayout } from "@/components/storefront/StoreLayout"
import { StoreBlogList } from "@/components/storefront/StoreBlogList"

// 與 StoreHomeView 同理：獨立 client component 以符合 RSC 函式 prop 限制。
//
// 不再用 key={page} 讓列表每翻一頁就整個重新掛載（Sprint 337，DEF-486）：那是列表自己管資料狀態時的做法，
// 現在載入由 useAsyncData 依 page／categoryId 重新請求；重新掛載會讓分類篩選列跟著消失再重抓，翻頁與換分類時畫面會閃一下。
export function StoreBlogView({ slug, page, categoryId }: { slug: string; page: number; categoryId?: string }) {
  return (
    <StoreLayout slug={slug} active="blog">
      {(profile) => <StoreBlogList slug={slug} tenantId={profile.tenantId} page={page} categoryId={categoryId} />}
    </StoreLayout>
  )
}
