import type { Metadata } from "next"
import { ArticleNotFoundPage } from "@/components/storefront/ArticleNotFound"

// 文章不存在（草稿、已下架、網址亂填；店鋪是好的）：真正的 HTTP 404＋noindex。
// 店鋪不存在是另一個畫面（stores/not-found.tsx）：由 stores/[slug]/layout.tsx 拋出，不會落到這裡。
export const metadata: Metadata = { title: "找不到文章" }

export default function ArticleNotFoundRoute() {
  return <ArticleNotFoundPage />
}
