import { afterEach, describe, expect, it, vi } from 'vitest'
import { isAuthFlowPath, landingPathForRole, loginHref, safeRedirectTarget } from './auth-redirect'

const ORIGIN = 'http://localhost:3000'

// ?redirect= 是使用者可控的輸入，登入成功後會被拿去導頁：任何站外網址都是開放轉址（open redirect）的入口。
// 這組測試編碼的是「為什麼擋」，所以每個惡意樣本都附上它想利用的瀏覽器行為。
describe('safeRedirectTarget：?redirect= 只接受同源站內路徑', () => {
  it('站內路徑原樣放行（含查詢字串與 hash，登入後要回到原本的畫面狀態）', () => {
    expect(safeRedirectTarget('/orders', ORIGIN)).toBe('/orders')
    expect(safeRedirectTarget('/products?category=a&page=2#top', ORIGIN)).toBe('/products?category=a&page=2#top')
  })

  it.each([
    ['空值', null],
    ['空字串', ''],
    ['undefined', undefined],
  ])('%s → null（改用角色預設落地頁）', (_label, raw) => {
    expect(safeRedirectTarget(raw, ORIGIN)).toBeNull()
  })

  it.each([
    ['絕對網址（站外）', 'https://evil.example/phish'],
    ['絕對網址（同源也不收：只認「/ 開頭的站內路徑」）', 'http://localhost:3000/orders'],
    ['協定相對網址 //host（瀏覽器補上目前協定→站外）', '//evil.example'],
    ['反斜線 /\\host（WHATWG URL 把 \\ 當成 /，等同 //host）', '/\\evil.example'],
    ['javascript: 協定（XSS）', 'javascript:alert(1)'],
    ['不是 / 開頭的相對路徑', 'orders'],
    ['正規化後變成 //host：/a/..//host', '/a/..//evil.example'],
  ])('擋下 %s', (_label, raw) => {
    expect(safeRedirectTarget(raw, ORIGIN)).toBeNull()
  })

  // 登入頁自己、註冊、OAuth 回呼都是「登入流程」：導回去會繞圈（登入→登入頁→登入…）。
  it.each(['/login', '/login?redirect=%2Forders', '/register', '/forgot-password', '/reset-password', '/verify-email', '/oauth/callback/google'])(
    '不導回登入流程頁面 %s',
    (raw) => {
      expect(safeRedirectTarget(raw, ORIGIN)).toBeNull()
    }
  )

  it('路徑只是「長得像」登入流程（/login-help、/registered）不算：比對是整段路徑或其子路徑', () => {
    expect(safeRedirectTarget('/login-help', ORIGIN)).toBe('/login-help')
    expect(safeRedirectTarget('/registered', ORIGIN)).toBe('/registered')
  })
})

describe('isAuthFlowPath', () => {
  it('完全相同或其子路徑才算', () => {
    expect(isAuthFlowPath('/login')).toBe(true)
    expect(isAuthFlowPath('/oauth/callback/google')).toBe(true)
    expect(isAuthFlowPath('/login-help')).toBe(false)
    expect(isAuthFlowPath('/')).toBe(false)
  })
})

describe('landingPathForRole：登入後依角色分流', () => {
  it.each([
    ['ADMIN', '/admin'],
    ['SUPER_ADMIN', '/admin'],
    ['STORE_OWNER', '/dashboard'],
    ['STORE_STAFF', '/dashboard'],
    ['SELLER', '/dashboard'],
    ['HOST', '/dashboard'],
    ['BUYER', '/'],
  ])('%s → %s', (role, expected) => {
    expect(landingPathForRole(role)).toBe(expected)
  })

  it('沒有角色或不認得的角色一律回商店首頁（最小權限的落地頁，不猜）', () => {
    expect(landingPathForRole(undefined)).toBe('/')
    expect(landingPathForRole(null)).toBe('/')
    expect(landingPathForRole('SOMETHING_NEW')).toBe('/')
  })
})

describe('loginHref：未登入牆的導向網址', () => {
  const setLocation = (pathname: string, search = '') => {
    vi.stubGlobal('window', { location: { pathname, search } })
  }
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('記住目前頁面（含查詢字串），登入後回得來', () => {
    setLocation('/orders/42', '?tab=logs')
    expect(loginHref()).toBe('/login?redirect=' + encodeURIComponent('/orders/42?tab=logs'))
  })

  it('人已經在登入流程頁面時不帶 redirect（否則登入後會導回登入頁）', () => {
    setLocation('/login', '?redirect=%2Forders')
    expect(loginHref()).toBe('/login')
    setLocation('/register')
    expect(loginHref()).toBe('/login')
  })

  it('伺服器端渲染（沒有 window）退回 /login，不丟例外', () => {
    vi.stubGlobal('window', undefined)
    expect(loginHref()).toBe('/login')
  })
})
