// 伺服器端（Server Component、generateMetadata）呼叫後端「公開」API 用（DEF-475，Sprint 335）。
//
// 為什麼不用 services／axios：那一層是給瀏覽器用的——憑證與租戶在 localStorage、基底網址是 NEXT_PUBLIC_API_URL（瀏覽器看到的網址）。
// 伺服器端沒有 localStorage，也可能要走不同的網址（例如容器網路裡的後端位址），所以另外一個設定：
//   API_INTERNAL_URL   伺服器端用的後端基底網址，預設 http://localhost:8080/api（與 lib/api.ts 的 NEXT_PUBLIC_API_URL 預設同形，路徑以 /v2/... 接在後面）
// 沒有 NEXT_PUBLIC_ 前綴＝只存在於伺服器，不會被打包進瀏覽器的程式。
//
// 失敗一律回 null、不丟例外：這裡只用來「補強」標題與分享資訊，後端慢或壞了，頁面照常顯示靜態的頁型標題，不能被它拖垮或拖慢。

const TIMEOUT_MS = 2000

const baseUrl = () => (process.env.API_INTERNAL_URL || 'http://localhost:8080/api').replace(/\/+$/, '')

/** GET <API_INTERNAL_URL><path>，成功回 ApiResponse 的 data；任何失敗（網路、逾時、非 2xx、非 JSON、沒有 data）回 null。 */
export async function fetchPublicData<T>(path: string): Promise<T | null> {
  try {
    const response = await fetch(`${baseUrl()}${path}`, {
      // 不快取：店名改了、店鋪被停權，標題要立刻反映；公開頁流量不大，多一次內網請求的成本可以接受
      cache: 'no-store',
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(TIMEOUT_MS),
    })
    if (!response.ok) return null
    const body = (await response.json()) as { data?: T | null }
    return body?.data ?? null
  } catch {
    return null
  }
}
