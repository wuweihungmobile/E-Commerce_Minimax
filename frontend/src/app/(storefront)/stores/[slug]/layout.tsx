import { loadStoreProfile } from "@/lib/public-data"

// 店鋪前台各頁共用的「店鋪存在嗎」檢查（SEO，DEF-500，Sprint 341）：店鋪不存在（或非 ACTIVE）→ 整個 /stores/{店鋪}/* 回真正的 404，
// 畫面是 stores/not-found.tsx 的「找不到這間店鋪」。檢查放在 layout 而不是每頁各寫一次，有兩個原因：
//   1. layout 拋出的 notFound() 由上一層的 not-found.tsx 接住，所以文章頁自己的 not-found（找不到文章，
//      [postSlug]/not-found.tsx）不會把「店鋪不存在」誤說成「找不到文章」——前提是頁面自己不再判定店鋪是否存在
//      （頁面用 peekStoreProfile，不丟 notFound）；
//   2. 店鋪相關的每一頁（首頁、商品、房源、貼文、文章）都不會漏掉這個檢查。
// 取店鋪資料的請求與各頁自己要的是同一份，同一個請求內只打一次後端（server-api 的請求內快取）。
export default async function StoreSegmentLayout({
  children,
  params,
}: {
  children: React.ReactNode
  params: Promise<{ slug: string }>
}) {
  const { slug } = await params
  await loadStoreProfile(slug)
  return children
}
