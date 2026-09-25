import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { API_CONFIG } from '@/lib/api';

/**
 * 嚴格 CSP（nonce + strict-dynamic）：只有帶本次請求 nonce 的 script 能執行，
 * 被注入的 inline script、inline 事件處理器（onerror=...）與外部 script 都會被瀏覽器擋下。
 * 本專案的 token 存在 localStorage，XSS 一旦成功就能竊取 token，所以這道防線特別重要。
 *
 * 代價：所有頁面必須動態渲染（每次請求一個新 nonce）；layout.tsx 讀取 x-nonce 標頭即會觸發。
 *
 * 緊急開關：執行環境設 CSP_REPORT_ONLY=1 改為只回報不攔截（瀏覽器主控台仍會列出違規），
 * 用於部署後發現 CSP 擋到未預期的資源時，不必改程式即可先恢復功能。
 */

/** 後端 API 來源（含 SockJS 的 ws/wss）：connect-src 需放行，來源與實際呼叫的 API_CONFIG 一致。 */
function backendOrigins(): { http: string; ws: string; isPlainHttp: boolean } | null {
  try {
    const url = new URL(API_CONFIG.baseUrl);
    return {
      http: url.origin,
      ws: url.origin.replace(/^http/, 'ws'),
      isPlainHttp: url.protocol === 'http:',
    };
  } catch {
    // baseUrl 為相對路徑（同源反向代理）：'self' 已涵蓋
    return null;
  }
}

function buildCsp(nonce: string): string {
  const isDev = process.env.NODE_ENV === 'development';
  const backend = backendOrigins();

  const connectSrc = ["'self'"];
  if (backend) connectSrc.push(backend.http, backend.ws);
  if (isDev) connectSrc.push('ws:', 'wss:');

  // 商品圖為使用者／後端提供的任意 https URL；後端本身是明文 http（本機／內網）時才連帶放行 http 圖片
  const imgSrc = ["'self'", 'data:', 'blob:', 'https:'];
  if (backend?.isPlainHttp) imgSrc.push('http:');

  return [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${isDev ? " 'unsafe-eval'" : ''}`,
    // style 刻意維持 'unsafe-inline'：React 的 style 屬性與部分函式庫會注入 <style>，
    // 樣式注入的風險遠低於 script，不值得為此犧牲相容性
    "style-src 'self' 'unsafe-inline'",
    `img-src ${imgSrc.join(' ')}`,
    "font-src 'self' data:",
    `connect-src ${connectSrc.join(' ')}`,
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
    // 刻意不加 upgrade-insecure-requests：在 http（本機／內網）部署會把打向後端的請求也升級成 https 而全部失敗
  ].join('; ');
}

export function proxy(request: NextRequest) {
  const nonce = Buffer.from(crypto.randomUUID()).toString('base64');
  const csp = buildCsp(nonce);

  // Next 會從「請求」的 CSP 標頭取出 nonce 套用到框架自己的 script；x-nonce 供 layout 給自訂 inline script 使用
  const requestHeaders = new Headers(request.headers);
  requestHeaders.set('x-nonce', nonce);
  requestHeaders.set('Content-Security-Policy', csp);

  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set(
    process.env.CSP_REPORT_ONLY === '1'
      ? 'Content-Security-Policy-Report-Only'
      : 'Content-Security-Policy',
    csp,
  );
  return response;
}

export const config = {
  matcher: [
    {
      // 排除靜態資源與 /api 反向代理路徑；預取請求不渲染頁面，不需要 CSP
      source: '/((?!api|_next/static|_next/image|favicon.ico).*)',
      missing: [
        { type: 'header', key: 'next-router-prefetch' },
        { type: 'header', key: 'purpose', value: 'prefetch' },
      ],
    },
  ],
};
