import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // 獨立伺服器輸出（Sprint 338，DEF-493，使用者明確指示）：frontend/Dockerfile 的 runner 階段 COPY .next/standalone 並以 node server.js 啟動，
  // 沒有這一行 next build 不會產生該目錄，正式（docker-compose.yml）與 CI 模擬（docker-compose.test.yml）的前端映像在 COPY 那一步就建不出來。
  // 與 Dockerfile 的一致性由 src/architecture/docker-standalone.test.ts 守門。
  // 對不經 Dockerfile 的路徑（本地 E2E 守門、雲端 CI、make up 的開發容器）：next build 多產出一份 .next/standalone，
  // next start 會印「does not work with output: standalone」警告（Next 原始碼只 warn、不擋，只有 output: export 才會丟錯）。
  output: 'standalone',
  poweredByHeader: false,
  // 安全標頭：頁面（含登入、結帳）原本不帶任何防點擊劫持／MIME 嗅探標頭。
  // 只放已查證不影響功能者——src 內無 iframe 嵌入、無第三方 script；OAuth 為整頁跳轉，不受影響。
  // CSP 需要每次請求的 nonce，改由 src/proxy.ts 產生。
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
        ],
      },
      {
        // HSTS 只能在 HTTPS 回應上送（瀏覽器對 http 回應中的 HSTS 一律忽略）。TLS 通常在前方的代理／負載平衡器終止，
        // 這裡收到的是 http，故以代理帶的 X-Forwarded-Proto 判斷；純 http（本機開發）不會帶，因此不會送。
        // 判斷與值都與後端（SecurityConfig.isHttpsRequest）一致：多層代理（https,http）取第一段、不分大小寫。
        // Next 以 new RegExp(`^${value}$`) 比對且不帶旗標，故用 [Hh].. 表示不分大小寫。
        // 不加 preload（需向瀏覽器廠商登記，幾乎無法反悔）；也不加 includeSubDomains——子網域是否都能走 https
        // 無從驗證，而該指示送出後一年內無法收回。
        source: '/:path*',
        has: [{ type: 'header', key: 'x-forwarded-proto', value: '\\s*[Hh][Tt][Tt][Pp][Ss]\\s*(,.*)?' }],
        headers: [{ key: 'Strict-Transport-Security', value: 'max-age=31536000' }],
      },
    ];
  },
  // 已移除的舊網址（Sprint 332，DEF-466）：/cms 是早期的內容管理後台，貼文與媒體介面已併進 /dashboard/posts、/dashboard/media。
  // 專案內沒有任何連結指向 /cms（PRD §10.1.2 查證過），只可能是書籤或手打的網址，所以逐一轉到對應的新頁面而不是統一丟回首頁；
  // 沒列在這裡的 /cms/* 仍是 404（沒有那個頁面）。permanent 的意思是 308：搬家是定案，不會再換回來。
  async redirects() {
    return [
      { source: '/cms', destination: '/dashboard/posts', permanent: true },
      { source: '/cms/posts/new', destination: '/dashboard/posts/new', permanent: true },
      { source: '/cms/posts/:id/edit', destination: '/dashboard/posts/:id/edit', permanent: true },
      { source: '/cms/media', destination: '/dashboard/media', permanent: true },
    ];
  },
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: 'http://ecommerce-backend-dev:8080/api/:path*',
      },
    ];
  },
};

export default nextConfig;
