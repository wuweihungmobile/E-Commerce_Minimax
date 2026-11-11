// @vitest-environment node
import { describe, expect, it } from 'vitest'
import nextConfig from '../../next.config'

// 已移除頁面的舊網址要轉到還在的頁面，不能直接 404（書籤、已分享的連結）。直接匯入 next.config.ts 看 redirects() 的回傳值，
// 不掃原始碼文字：註解裡出現同樣的字不會讓它誤過。

describe('舊網址轉址（next.config.ts redirects）', () => {
  it('平台層分類頁 /blog/category/{id}（Sprint 339，DEF-495 移除）→ /blog，308 永久轉址', async () => {
    // 為什麼移除：分類是店鋪內的概念，跨店鋪的 /blog 沒有分類；店鋪內的分類篩選在 /stores/{店鋪}/blog?category=（Sprint 337）。
    // 為什麼轉到 /blog 而不是對應的店鋪篩選頁：舊網址只有分類編號、沒有店鋪，匿名訪客無從查出它屬於哪間店鋪。
    const redirects = (await nextConfig.redirects?.()) ?? []
    expect(redirects).toContainEqual({ source: '/blog/category/:id', destination: '/blog', permanent: true })
  })

  it('既有的 /cms 轉址沒有被動到（Sprint 332，DEF-466）', async () => {
    const redirects = (await nextConfig.redirects?.()) ?? []
    expect(redirects).toContainEqual({ source: '/cms', destination: '/dashboard/posts', permanent: true })
  })
})
