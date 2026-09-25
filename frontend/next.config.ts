import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  poweredByHeader: false,
  // 安全標頭：頁面（含登入、結帳）原本不帶任何防點擊劫持／MIME 嗅探標頭。
  // 只放已查證不影響功能者——src 內無 iframe 嵌入、無第三方 script；OAuth 為整頁跳轉，不受影響。
  // 尚未加 script-src 型 CSP：需 nonce（強制全站動態渲染），屬架構決策，見 SPRINT_197_PLAN。
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
