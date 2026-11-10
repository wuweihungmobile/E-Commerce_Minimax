import { describe, expect, it } from 'vitest'
import { articlePath, storeBlogPath } from './article-path'

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

// 店鋪貼文列表的網址（Sprint 337，DEF-486）：文章頁的分類徽章、列表頁的篩選列與分頁都靠它。
describe('storeBlogPath', () => {
  it('什麼都不帶 → /stores/{店鋪}/blog（全部、第一頁）', () => {
    expect(storeBlogPath('demo-shop')).toBe('/stores/demo-shop/blog')
    expect(storeBlogPath('demo-shop', {})).toBe('/stores/demo-shop/blog')
  })

  it('帶分類 → ?category=；分類編號沒有值（undefined／null／空字串）一律當作「全部」，不帶 category', () => {
    expect(storeBlogPath('demo-shop', { categoryId: 'c-1' })).toBe('/stores/demo-shop/blog?category=c-1')
    for (const none of [undefined, null, '']) {
      expect(storeBlogPath('demo-shop', { categoryId: none }), String(none)).toBe('/stores/demo-shop/blog')
    }
  })

  it('頁碼是 1-based：第 1 頁不帶 page、第 2 頁起才帶（與 URL 慣例及 server page 的解析一致）', () => {
    expect(storeBlogPath('demo-shop', { page: 1 })).toBe('/stores/demo-shop/blog')
    expect(storeBlogPath('demo-shop', { page: 2 })).toBe('/stores/demo-shop/blog?page=2')
    expect(storeBlogPath('demo-shop', { page: 0 })).toBe('/stores/demo-shop/blog')
  })

  it('分類與頁碼一起帶：category 在前、page 在後（翻頁時不能掉了分類）', () => {
    expect(storeBlogPath('demo-shop', { categoryId: 'c-1', page: 3 })).toBe('/stores/demo-shop/blog?category=c-1&page=3')
  })

  it('店鋪代稱與分類編號都做 URL 編碼（來自後端資料與網址，不能把特殊字元原樣塞進去）', () => {
    expect(storeBlogPath('a b', { categoryId: 'x&y=z' })).toBe('/stores/a%20b/blog?category=x%26y%3Dz')
  })
})
