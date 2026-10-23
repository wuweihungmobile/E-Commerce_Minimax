// 登入後去哪裡（DEF-409，Sprint 301，使用者拍板）：依角色分流，並支援同源的 ?redirect= 回到原頁。

const LOGIN_PATH = '/login'

// 登入、註冊、密碼與驗證流程、OAuth 回呼都不能當作「登入後回去的頁面」（會繞圈）
const AUTH_FLOW_PATHS = ['/login', '/register', '/forgot-password', '/reset-password', '/verify-email', '/oauth']

/** 是不是登入、註冊、密碼與驗證流程、OAuth 回呼這類頁面：不需要登入，也不能當作「登入後回去的頁面」。 */
export function isAuthFlowPath(pathname: string): boolean {
  return AUTH_FLOW_PATHS.some((p) => pathname === p || pathname.startsWith(`${p}/`))
}

/** 依角色決定登入後的預設落地頁：買家回商店首頁、店家類進賣家後台、管理員進平台管理台。 */
export function landingPathForRole(role?: string | null): string {
  switch (role) {
    case 'ADMIN':
    case 'SUPER_ADMIN':
      return '/admin'
    case 'STORE_OWNER':
    case 'STORE_STAFF':
    case 'SELLER':
    case 'HOST':
      return '/dashboard'
    default:
      return '/'
  }
}

/**
 * 驗證 ?redirect= 的值：只接受「同源的站內路徑」，其餘一律回 null（改用預設落地頁）。
 *
 * router.push 不可傳入未驗證的網址（Next 文件：會有 XSS 風險）；`//host`、`/\host`、`https://…`、`javascript:`、
 * 以及正規化後變成 `//host` 的 `/a/..//host` 都是開放轉址或 XSS 的手法，這裡一律擋下。
 */
export function safeRedirectTarget(raw: string | null | undefined, origin: string): string | null {
  if (!raw || !raw.startsWith('/')) return null
  let url: URL
  try {
    url = new URL(raw, origin)
  } catch {
    return null
  }
  if (url.origin !== origin) return null
  if (isAuthFlowPath(url.pathname)) return null
  const target = url.pathname + url.search + url.hash
  // 正規化之後仍不得以 `//` 開頭（瀏覽器會當成協定相對網址）
  if (target.startsWith('//')) return null
  return target
}

/**
 * 要求使用者先登入時的導向網址：記住目前所在的頁面，登入後回來（遇 401、沒有 token 的未登入牆用；登出不要用，
 * 登出後登入不該回到剛離開的頁面）。伺服器端渲染時沒有 window，退回 `/login`。
 */
export function loginHref(): string {
  if (typeof window === 'undefined') return LOGIN_PATH
  const { pathname, search } = window.location
  if (isAuthFlowPath(pathname)) return LOGIN_PATH
  return `${LOGIN_PATH}?redirect=${encodeURIComponent(pathname + search)}`
}
