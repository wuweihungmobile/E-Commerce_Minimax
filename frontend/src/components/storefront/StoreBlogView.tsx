"use client"

import { StoreLayout } from "@/components/storefront/StoreLayout"
import { StoreBlogList } from "@/components/storefront/StoreBlogList"

// 與 StoreHomeView 同理：獨立 client component 以符合 RSC 函式 prop 限制。
export function StoreBlogView({ slug, page }: { slug: string; page: number }) {
  return (
    <StoreLayout slug={slug} active="blog">
      {(profile) => <StoreBlogList key={page} slug={slug} tenantId={profile.tenantId} page={page} />}
    </StoreLayout>
  )
}
