import type { MetadataRoute } from 'next'
import { SITE_URL } from '@/lib/site'

// robots.txt（DEF-500，Sprint 341）：公開內容（首頁、商品、房源、店鋪、部落格）全部開放，並指向 sitemap.xml；
// 後端 API 反向代理（/api）與兩個管理介面（/dashboard、/admin）、OAuth 回呼、健康檢查不需要被抓取。
// 登入、註冊、購物車、結帳、訂單等個人頁面（(auth) 區域）不放進這裡而是用 noindex（見 app/(auth)/layout.tsx）：
// 它們不會被連結成內容，爬蟲偶爾走到時看見 noindex 就會記住不收錄；這裡只擋「沒有任何理由被抓」的路徑，省下爬取額度。
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [{ userAgent: '*', allow: '/', disallow: ['/api/', '/dashboard', '/admin', '/oauth/', '/health'] }],
    sitemap: `${SITE_URL}/sitemap.xml`,
  }
}
