import { describe, expect, it } from 'vitest'
import { articlePath } from './article-path'

// 文章網址的形狀（Sprint 336，DEF-483）：產生文章連結的三個地方都靠它，形狀錯了所有文章連結都壞。
describe('articlePath', () => {
  it('店鋪代稱＋文章代稱 → /stores/{店鋪}/blog/{文章}', () => {
    expect(articlePath('demo-shop', 'hello-world')).toBe('/stores/demo-shop/blog/hello-world')
  })

  it('兩段都做 URL 編碼（代稱來自後端資料，不能把特殊字元原樣塞進路徑）', () => {
    expect(articlePath('a b', 'x/y?z#w')).toBe('/stores/a%20b/blog/x%2Fy%3Fz%23w')
  })

  it('店鋪代稱缺失（null／undefined／空字串）→ 退回舊網址 /blog/{文章}，不能產生 /stores/undefined/…', () => {
    for (const missing of [null, undefined, '']) {
      expect(articlePath(missing, 'hello-world'), String(missing)).toBe('/blog/hello-world')
    }
  })
})
