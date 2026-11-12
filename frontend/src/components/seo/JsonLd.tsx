// 結構化資料（JSON-LD）的輸出（DEF-500，Sprint 341）。資料來自 lib/seo.ts 的 *JsonLd 函式。
//
// 為什麼要跳脫 `<`：資料裡有店家與使用者輸入的文字（商品名稱、說明、文章標題）。JSON.stringify 不會處理 HTML，
// 內容若含 `</script><script>…` 會提前結束標籤並注入腳本；把 `<` 換成 \u003c 之後，JSON 的意思不變、HTML 解析器看不到標籤。
// 也跳脫 U+2028／U+2029（舊的 JavaScript 引擎把它們當成換行，JSON 字串裡出現會中斷）。
// CSP 不受影響：type="application/ld+json" 是資料區塊，瀏覽器不執行，script-src 不管它（所以不需要 nonce）。

export function jsonLdText(data: unknown): string {
  return JSON.stringify(data).replace(/</g, '\\u003c').replace(/\u2028/g, '\\u2028').replace(/\u2029/g, '\\u2029')
}

/** data 為 null（資料不足、不該輸出）時什麼都不渲染。 */
export function JsonLd({ data }: { data: Record<string, unknown> | null }) {
  if (!data) return null
  return <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: jsonLdText(data) }} />
}
