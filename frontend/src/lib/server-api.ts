// 伺服器端（Server Component、generateMetadata）呼叫後端「公開」API 用（DEF-475，Sprint 335；Sprint 341 擴充為 SEO 的伺服器端渲染用）。
//
// 為什麼不用 services／axios：那一層是給瀏覽器用的——憑證與租戶在 localStorage、基底網址是 NEXT_PUBLIC_API_URL（瀏覽器看到的網址）。
// 伺服器端沒有 localStorage，也可能要走不同的網址（例如容器網路裡的後端位址），所以另外一個設定：
//   API_INTERNAL_URL   伺服器端用的後端基底網址，預設 http://localhost:8080/api（與 lib/api.ts 的 NEXT_PUBLIC_API_URL 預設同形，路徑以 /v2/... 接在後面）
// 沒有 NEXT_PUBLIC_ 前綴＝只存在於伺服器，不會被打包進瀏覽器的程式。
//
// 失敗一律不丟例外：標題、分享資訊與首屏內容都只是「補強」，後端慢或壞了，頁面照常顯示（退回靜態標題；內容改由瀏覽器載入），
// 不能被它拖垮或拖慢。結果分三種（PublicFetchResult）：成功、後端明確說「不存在」（404）、其餘一切失敗（不可用）。
// 分開「不存在」與「不可用」是 SEO 的要求：不存在的頁面要回真正的 404（否則搜尋引擎把錯誤頁當成內容收錄，即所謂 soft 404），
// 但後端只是暫時壞了（429、5xx、逾時）不能回 404——那會讓搜尋引擎把一個好好的頁面除名。
//
// 同一個請求內相同路徑只打一次（React cache）：generateMetadata 與頁面本體常常要同一份資料（店鋪資料、文章），不必各打一次後端。
//
// 訪客的來源位址（DEF-487／DEF-501，Sprint 341）：後端依來源 IP 限流。Next 伺服器代訪客呼叫後端時，來源是 Next 伺服器自己，
// 所有訪客會共用同一個匿名限流桶。所以把收到的 X-Forwarded-For「原封不動」帶給後端（不附加、不改寫）：
// 前面有反向代理時（後端設了 APP_TRUSTED_PROXY_HOPS），後端據此把請求算在訪客自己的桶；沒有代理時後端不採信這個標頭，行為不變。
// 為什麼不附加：「訪客→代理→Next→後端」與「訪客→代理→後端」到後端必須是同樣的 N 個項目，同一個層數設定兩條路才都對。
// 不需要、也沒有前端自己的代理設定。細節見 docs/08_deployment/ENVIRONMENT_VARIABLES.md §2.5.1。

import { cache } from 'react'
import { headers } from 'next/headers'

const TIMEOUT_MS = 2000

const baseUrl = () => (process.env.API_INTERNAL_URL || 'http://localhost:8080/api').replace(/\/+$/, '')

export type PublicFetchResult<T> =
  /** 成功：ApiResponse 的 data（不是 null） */
  | { status: 'ok'; data: T }
  /** 後端明確回 404：資源不存在（對匿名訪客，停權／未審核的店鋪與不存在的店鋪刻意不分，見 StoreLayout） */
  | { status: 'notfound' }
  /** 其餘一切失敗：網路、逾時、429、5xx、非 JSON、沒有 data。呼叫端退回靜態標題／讓瀏覽器自己載入，不能當成「不存在」 */
  | { status: 'unavailable' }

/** 目前這個請求收到的 X-Forwarded-For；不在請求範圍內（例如單元測試、建置時）或沒有這個標頭回 undefined。 */
async function incomingForwardedFor(): Promise<string | undefined> {
  try {
    return (await headers()).get('x-forwarded-for') ?? undefined
  } catch {
    return undefined
  }
}

const fetchPublicResult = cache(async (path: string): Promise<PublicFetchResult<unknown>> => {
  try {
    const forwardedFor = await incomingForwardedFor()
    const response = await fetch(`${baseUrl()}${path}`, {
      // 不快取：店名改了、店鋪被停權，內容與標題要立刻反映；多一次內網請求的成本可以接受（限流的影響見檔頭 DEF-487）
      cache: 'no-store',
      headers: { Accept: 'application/json', ...(forwardedFor ? { 'X-Forwarded-For': forwardedFor } : {}) },
      signal: AbortSignal.timeout(TIMEOUT_MS),
    })
    if (response.status === 404) return { status: 'notfound' }
    if (!response.ok) return { status: 'unavailable' }
    const body = (await response.json()) as { data?: unknown }
    return body?.data == null ? { status: 'unavailable' } : { status: 'ok', data: body.data }
  } catch {
    return { status: 'unavailable' }
  }
})

/** GET <API_INTERNAL_URL><path>，回三種結果之一（見 PublicFetchResult）；不丟例外。 */
export function fetchPublic<T>(path: string): Promise<PublicFetchResult<T>> {
  return fetchPublicResult(path) as Promise<PublicFetchResult<T>>
}

/** GET <API_INTERNAL_URL><path>，成功回 ApiResponse 的 data；任何失敗（含 404）回 null。只需要「有沒有」的呼叫端（標題）用。 */
export async function fetchPublicData<T>(path: string): Promise<T | null> {
  const result = await fetchPublic<T>(path)
  return result.status === 'ok' ? result.data : null
}

/**
 * 成功時取出資料，其餘（不存在、不可用）回 undefined。傳給元件的 initialXxx 屬性用：沒取到就不帶，讓瀏覽器自己載入
 * （「不存在」要不要回 404 是各頁自己決定的事，見 lib/public-data.ts）。
 */
export function dataOrUndefined<T>(result: PublicFetchResult<T>): T | undefined {
  return result.status === 'ok' ? result.data : undefined
}
