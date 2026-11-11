import { PlatformBlogFeed } from "@/components/storefront/PlatformBlogFeed"

// 平台層部落格（跨店鋪，匿名可讀；Sprint 339，DEF-495）。頁碼由網址 ?page=（1-based）決定，這裡解析成 0-based 交給列表元件，
// 做法與 /stores/[slug]/blog 相同（DEF-020 慣例）：壞值（亂填、0、負數、重複的參數）一律當第一頁。
// 頁面標題在同目錄的 layout.tsx。
export default async function BlogPage({
  searchParams,
}: {
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>
}) {
  const sp = await searchParams
  const pageRaw = typeof sp.page === "string" ? parseInt(sp.page, 10) : 1
  const page = Number.isFinite(pageRaw) && pageRaw > 0 ? pageRaw - 1 : 0

  return <PlatformBlogFeed page={page} />
}
