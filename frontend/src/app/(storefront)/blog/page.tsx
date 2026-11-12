import { PlatformBlogFeed } from "@/components/storefront/PlatformBlogFeed"
import { LIST_PAGE_SIZE, publicPostFeedPath } from "@/lib/public-api"
import { dataOrUndefined, fetchPublic } from "@/lib/server-api"
import type { PublicPostFeed } from "@/services/cms"

// 平台層部落格（跨店鋪，匿名可讀；Sprint 339，DEF-495）。頁碼由網址 ?page=（1-based）決定，這裡解析成 0-based 交給列表元件，
// 做法與 /stores/[slug]/blog 相同（DEF-020 慣例）：壞值（亂填、0、負數、重複的參數）一律當第一頁。
// 頁面標題在同目錄的 layout.tsx。
//
// SEO（DEF-500，Sprint 341）：這一頁的文章列表在「伺服器端」取（GET /v2/public/posts）、隨第一份 HTML 送出——搜尋引擎與
// 不執行 JavaScript 的爬蟲看得到文章卡片與連結。取不到（逾時、後端壞了）就不帶資料，列表元件退回原本由瀏覽器自己載入。
// key 含頁碼：換頁時元件重新掛載，直接用新一頁的伺服器資料（不再多打一次請求、也沒有載入中的閃動）。
export default async function BlogPage({
  searchParams,
}: {
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>
}) {
  const sp = await searchParams
  const pageRaw = typeof sp.page === "string" ? parseInt(sp.page, 10) : 1
  const page = Number.isFinite(pageRaw) && pageRaw > 0 ? pageRaw - 1 : 0

  const feed = await fetchPublic<PublicPostFeed>(publicPostFeedPath(page, LIST_PAGE_SIZE))

  return <PlatformBlogFeed key={page} page={page} initialFeed={dataOrUndefined(feed)} />
}
