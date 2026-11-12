// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { EMBED_LISTING_PATTERN, embeddedListingIds } from './article-embeds'

// 文章內文裡的商品／房源嵌入語法 {{embed:listing:<編號>}}（PRD §6.6.3）。文章元件（把語法換成商品卡片）與伺服器端渲染
// （先取好每張卡片）共用這一份定義（Sprint 341）。內文是店家寫的，不能信任：格式不對的語法不是嵌入，要維持純文字，
// 也不能讓它變成一個送往後端的請求。

const A = '0b135be9-bf30-4ddb-ab3f-092f3e26b845'
const B = '1c246cfa-c041-4eec-bc40-1a4f3f37c956'

describe('embeddedListingIds', () => {
  it('依出現順序取出編號，重複的只算一次', () => {
    expect(embeddedListingIds(`開頭 {{embed:listing:${A}}} 中間 {{embed:listing:${B}}} 又一次 {{embed:listing:${A}}}`)).toEqual([A, B])
  })

  it('同一行、沒有空白分隔的多個嵌入也都取得到', () => {
    expect(embeddedListingIds(`{{embed:listing:${A}}}{{embed:listing:${B}}}`)).toEqual([A, B])
  })

  it('編號只認十六進位字元與連字號：含其他字元的語法不是嵌入（維持純文字，不會變成請求）', () => {
    const malformed = [
      '{{embed:listing:abc xyz}}',
      '{{embed:listing:../../etc/passwd}}',
      '{{embed:listing:}}',
      '{{embed:listing:g1234}}',
      '{{embed:listing:<script>}}',
      '{{embed:product:' + A + '}}',
      '{embed:listing:' + A + '}',
    ]
    for (const text of malformed) expect(embeddedListingIds(text), text).toEqual([])
  })

  it('大小寫不拘', () => {
    expect(embeddedListingIds(`{{EMBED:LISTING:${A.toUpperCase()}}}`)).toEqual([A.toUpperCase()])
  })

  it('沒有嵌入、空字串 → 空陣列', () => {
    expect(embeddedListingIds('一般的文章內容')).toEqual([])
    expect(embeddedListingIds('')).toEqual([])
  })

  it('連續呼叫結果一致（內部每次用新的 RegExp，不受 g 旗標的 lastIndex 狀態影響）', () => {
    const text = `{{embed:listing:${A}}}`
    expect(embeddedListingIds(text)).toEqual([A])
    expect(embeddedListingIds(text)).toEqual([A])
    expect(EMBED_LISTING_PATTERN.lastIndex).toBe(0)
  })
})
