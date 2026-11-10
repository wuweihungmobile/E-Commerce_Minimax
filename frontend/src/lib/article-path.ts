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

/**
 * 店鋪貼文列表的網址（Sprint 337，DEF-486）：/stores/{店鋪}/blog，可帶分類篩選與頁碼。
 *
 * 公開版的分類篩選：分類由網址的 category 決定（不再靠瀏覽器 localStorage 的 tenantId，匿名訪客也能用）。
 * 文章頁的分類徽章與列表頁的篩選列、分頁都從這裡取，網址形狀只寫一處。
 *
 * @param storeSlug 店鋪的網址代稱
 * @param options.categoryId 分類編號；沒有（null／undefined／空字串）＝全部，不帶 category
 * @param options.page 1-based 頁碼（網址慣例）；沒有或 1＝第一頁，不帶 page
 *
 * 查詢字串只帶有值的項目，category 在前、page 在後；切換分類時呼叫端不傳 page，所以一定回到第一頁。
 */
export function storeBlogPath(
  storeSlug: string,
  options: { categoryId?: string | null; page?: number } = {}
): string {
  const base = `/stores/${encodeURIComponent(storeSlug)}/blog`
  const params = new URLSearchParams()
  if (options.categoryId) params.set('category', options.categoryId)
  if (options.page !== undefined && options.page > 1) params.set('page', String(options.page))
  const query = params.toString()
  return query ? `${base}?${query}` : base
}
