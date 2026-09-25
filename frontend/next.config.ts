import type { NextConfig } from "next";

const nextConfig: NextConfig = {
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
        // 值與後端（SecurityConfig）一致；不加 preload——那需要向瀏覽器廠商登記，且幾乎無法反悔。
        source: '/:path*',
        has: [{ type: 'header', key: 'x-forwarded-proto', value: 'https.*' }],
        headers: [{ key: 'Strict-Transport-Security', value: 'max-age=31536000; includeSubDomains' }],
      },
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
