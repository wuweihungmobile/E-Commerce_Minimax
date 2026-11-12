// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// 伺服器端呼叫後端公開 API（lib/server-api.ts）的三種結果與訪客位址的轉發（DEF-487／DEF-501／DEF-500，Sprint 341）。
// 標題用的 fetchPublicData 的既有行為見 seo.test.ts；這裡守的是新增的部分：
//   ① 結果分三種——成功、後端明確回 404（不存在）、其餘一切失敗（不可用）。分開是 SEO 的要求：不存在的頁面要回真正的 404，
//      但後端只是暫時壞了（429、5xx、逾時）絕不能回 404，否則搜尋引擎把好好的頁面除名；
//   ② 訪客的 X-Forwarded-For「原封不動」帶給後端（不附加、不改寫）：前面有反向代理時（後端設了 APP_TRUSTED_PROXY_HOPS）
//      後端據此把請求算在訪客自己的限流桶；「訪客→代理→Next→後端」與「訪客→代理→後端」到後端必須是同樣的 N 個項目。

const incoming = vi.hoisted(() => ({ headers: new Headers() as Headers | Error }))
vi.mock('next/headers', () => ({
  headers: async () => {
    if (incoming.headers instanceof Error) throw incoming.headers
    return incoming.headers
  },
}))

import { dataOrUndefined, fetchPublic, fetchPublicData } from './server-api'

const fetchMock = vi.fn()
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const sentHeaders = () => (fetchMock.mock.calls[0][1] as RequestInit).headers as Record<string, string>

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubEnv('API_INTERNAL_URL', '')
  incoming.headers = new Headers()
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

describe('fetchPublic：三種結果', () => {
  it('成功：回 ApiResponse 的 data', async () => {
    fetchMock.mockResolvedValue(json({ data: { name: '山海小店' } }))
    expect(await fetchPublic('/v2/public/stores/demo')).toEqual({ status: 'ok', data: { name: '山海小店' } })
  })

  it('後端明確回 404 → notfound（資源不存在；對匿名訪客，停權與不存在的店鋪刻意不分）', async () => {
    fetchMock.mockResolvedValue(json({ code: 'E-2000' }, 404))
    expect(await fetchPublic('/v2/public/stores/gone')).toEqual({ status: 'notfound' })
  })

  it.each([429, 500, 502, 503])('HTTP %i → unavailable，絕不是 notfound（後端暫時壞了不能讓搜尋引擎把好好的頁面除名）', async (status) => {
    fetchMock.mockResolvedValue(json({}, status))
    expect(await fetchPublic('/v2/x')).toEqual({ status: 'unavailable' })
  })

  it('網路錯誤、逾時、非 JSON、成功但沒有 data → 一律 unavailable', async () => {
    fetchMock.mockRejectedValue(new Error('connect ECONNREFUSED'))
    expect(await fetchPublic('/v2/x')).toEqual({ status: 'unavailable' })
    fetchMock.mockRejectedValue(new DOMException('The operation was aborted due to timeout', 'TimeoutError'))
    expect(await fetchPublic('/v2/x')).toEqual({ status: 'unavailable' })
    fetchMock.mockResolvedValue(new Response('<html>502</html>', { status: 200 }))
    expect(await fetchPublic('/v2/x')).toEqual({ status: 'unavailable' })
    fetchMock.mockResolvedValue(json({ success: true }))
    expect(await fetchPublic('/v2/x')).toEqual({ status: 'unavailable' })
    fetchMock.mockResolvedValue(json({ data: null }))
    expect(await fetchPublic('/v2/x')).toEqual({ status: 'unavailable' })
  })

  it('fetchPublicData（標題用）：成功回資料，不存在與不可用都回 null；dataOrUndefined 對應成 undefined', async () => {
    fetchMock.mockResolvedValue(json({ data: { a: 1 } }))
    expect(await fetchPublicData('/v2/x')).toEqual({ a: 1 })
    fetchMock.mockResolvedValue(json({}, 404))
    expect(await fetchPublicData('/v2/x')).toBeNull()
    expect(dataOrUndefined({ status: 'ok', data: 5 })).toBe(5)
    expect(dataOrUndefined({ status: 'notfound' })).toBeUndefined()
    expect(dataOrUndefined({ status: 'unavailable' })).toBeUndefined()
  })
})

describe('訪客位址的轉發（X-Forwarded-For）', () => {
  it('收到什麼就原封不動帶給後端：單一位址、代理附加的多個位址（不附加、不改寫、不去重）', async () => {
    fetchMock.mockResolvedValue(json({ data: {} }))
    for (const value of ['203.0.113.7', '1.2.3.4, 203.0.113.7', '203.0.113.7, 198.51.100.1', '::ffff:127.0.0.1']) {
      fetchMock.mockClear()
      incoming.headers = new Headers({ 'x-forwarded-for': value })
      await fetchPublic('/v2/x')
      expect(sentHeaders()['X-Forwarded-For'], `收到「${value}」`).toBe(value)
    }
  })

  it('沒有收到 X-Forwarded-For 就不帶（不自己編一個）', async () => {
    fetchMock.mockResolvedValue(json({ data: {} }))
    await fetchPublic('/v2/x')
    expect(sentHeaders()).not.toHaveProperty('X-Forwarded-For')
    expect(sentHeaders().Accept).toBe('application/json')
  })

  it('不轉發其他代理標頭（只有 X-Forwarded-For 是限流需要的；Cookie、Authorization 絕不帶給公開端點）', async () => {
    fetchMock.mockResolvedValue(json({ data: {} }))
    incoming.headers = new Headers({
      'x-forwarded-for': '203.0.113.7',
      'x-forwarded-proto': 'https',
      'x-forwarded-host': 'evil.example',
      cookie: 'session=secret',
      authorization: 'Bearer secret',
    })
    await fetchPublic('/v2/x')
    expect(Object.keys(sentHeaders()).sort()).toEqual(['Accept', 'X-Forwarded-For'])
  })

  it('不在請求範圍內（headers() 丟例外，例如建置時、單元測試）→ 照常請求，只是不帶 X-Forwarded-For', async () => {
    incoming.headers = new Error('headers() was called outside a request scope')
    fetchMock.mockResolvedValue(json({ data: { ok: 1 } }))
    expect(await fetchPublic('/v2/x')).toEqual({ status: 'ok', data: { ok: 1 } })
    expect(sentHeaders()).not.toHaveProperty('X-Forwarded-For')
  })
})
