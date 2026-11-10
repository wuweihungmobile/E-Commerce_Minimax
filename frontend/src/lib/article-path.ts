// 文章的公開網址（Sprint 336，DEF-483）：/stores/{店鋪}/blog/{文章}。
//
// 原本是 /blog/{文章}，沒有店鋪——那一頁靠瀏覽器 localStorage 的 tenantId 取文章，匿名訪客打不開，
// 伺服器端也查不到文章（要 tenantId），所以沒有標題。網址帶店鋪之後，店鋪由網址決定，兩個問題一起解決。
// 產生文章連結的地方（部落格列表、分類頁、店鋪貼文列表）都從這裡取，網址形狀只寫一處。

/**
 * @param storeSlug 店鋪的網址代稱（後端文章回應的 tenantSlug）
 * @param postSlug  文章的網址代稱
 *
 * 店鋪代稱缺失（前端比後端先部署的那幾分鐘，文章回應還沒有 tenantSlug）時退回舊網址 /blog/{文章}：
 * 舊網址會在登入者的瀏覽器裡找出文章所屬店鋪再導向新網址，不會變成 /stores/undefined/…。
 */
export function articlePath(storeSlug: string | null | undefined, postSlug: string): string {
  const post = encodeURIComponent(postSlug)
  return storeSlug ? `/stores/${encodeURIComponent(storeSlug)}/blog/${post}` : `/blog/${post}`
}
