// 文章內文裡的商品／房源嵌入語法 {{embed:listing:<id>}}（PRD §6.6.3）。
//
// 文章元件（把語法換成商品卡片）、伺服器端渲染（先取好每張卡片的資料，SEO 的第一份 HTML 就有商品）、分享資訊的說明文字
// （語法不是人讀的文字，要去掉）都要認得它——語法只寫在這一處。

/** 嵌入語法；source 給需要自己建 RegExp（g 旗標有狀態，不能共用同一個物件）的呼叫端。 */
export const EMBED_LISTING_PATTERN = /{{embed:listing:([a-f0-9-]+)}}/gi

/** 內文裡依出現順序、去除重複的商品／房源編號（尚未驗證格式）。 */
export function embeddedListingIds(content: string): string[] {
  const ids = new Set<string>()
  for (const match of content.matchAll(new RegExp(EMBED_LISTING_PATTERN.source, 'gi'))) ids.add(match[1])
  return [...ids]
}
