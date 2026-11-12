import { useCallback, useEffect, useState } from 'react'
import { getApiErrorInfo, type ApiErrorInfo } from '@/lib/api-error'

// 頁面載入資料的共用 hook（Sprint 331，DEF-456）。
//
// Sprint 331 之前，「載入中 → 成功／失敗 → 重試」由各頁自己寫：約 70 個頁面／元件各自宣告 loading／error／data 三個 state，
// 在 useEffect 裡呼叫自己的 fetchXxx，並各自（或沒有）處理競態：篩選條件連續改變時，先送出的請求若較晚回來，會蓋掉後送出的結果；
// 元件卸載後回應才到也照寫 state。這裡把這套邏輯放在一個地方，行為有單元測試（useAsyncData.test.tsx）。
//
//   const { data, error, loading, reload } = useAsyncData(
//     (signal) => adminService.getTenant(tenantId, { signal }),
//     [tenantId],
//     { errorMessage: '載入店鋪詳情失敗' }
//   )
//
// 設計重點（改的時候別破壞，見 ADR-002）：
// 1. loading／error 是「衍生值」，不在 effect 裡同步 setState：state 只記「最近一次完成的請求」的鍵與結果，
//    loading ＝ 目前的鍵與完成的鍵不同。依賴一變，同一個 render 就已經是 loading（不會出現「舊資料配 loading=false」的一格畫面），
//    而且符合 React 19 的 react-hooks/set-state-in-effect（effect 只啟動請求，結果在非同步回呼裡寫入）。
// 2. 競態：每次 effect 建一個 AbortController，依賴改變／重試／卸載時 abort；回應回來先檢查 signal.aborted 才寫入。
//    即使 fetcher 沒有把 signal 轉給 axios（舊的 service 方法），舊回應也不會蓋掉新的。
//    fetcher 若把 signal 傳給 axios，被中止的請求會真的取消；取消（CanceledError）不算錯誤。
// 3. 呼叫端的依賴陣列由 eslint 的 react-hooks/exhaustive-deps（additionalHooks）檢查，漏列會被擋下。
// 4. initialData（Sprint 341，DEF-500，SEO 的伺服器端渲染）：伺服器端已經取好「第一輪依賴」的結果時，首次渲染直接用它——
//    loading 為 false、不發請求，搜尋引擎與不執行 JavaScript 的爬蟲拿到的第一份 HTML 就有內容。它只對第一輪有效：
//    依賴改變、reload() 之後照常重新請求。伺服器端沒取到（逾時、後端壞了）就不要傳，行為與先前完全相同（瀏覽器自己載入）。
//    要在換頁／換篩選時也直接用新的伺服器資料，呼叫端用 key 讓元件重新掛載（見 HomeContent），不靠這個 hook 猜哪份資料對應哪組依賴。

export interface UseAsyncDataOptions<T = unknown> {
  /** false 時不發請求（例如還沒拿到路由參數）：loading 為 false，data 維持上一次的值。預設 true。 */
  enabled?: boolean
  /**
   * 失敗時的訊息。字串＝後備文案：有伺服器的 message 就優先用它（與各頁原本的 `message || fallback` 相同）；
   * 函式＝需要依 HTTP 狀態分流的頁面（例如 404 顯示「找不到」）自己決定整句，參數是解析後的錯誤資訊與原始錯誤。
   */
  errorMessage: string | ((info: ApiErrorInfo, error: unknown) => string)
  /**
   * 這一輪載入失敗時呼叫一次（訊息＝上面算出來的 errorMessage，與原始錯誤）。給「失敗只用 toast 通知、沒有頁面級錯誤畫面」的頁面用，
   * 例如 onError: (message) => toast.error(message)。被中止或已過期的請求不會呼叫。
   */
  onError?: (message: string, error: unknown) => void
  /**
   * 伺服器端已經取好的「第一輪依賴」的資料（SEO 的伺服器端渲染）。有值時首次渲染 loading 為 false、不發請求；
   * 之後依賴改變或 reload() 照常請求。undefined（伺服器端沒取到）時與沒有這個選項完全相同。
   */
  initialData?: T
}

export interface UseAsyncDataResult<T> {
  /** 最近一次成功的資料；重新載入期間保留（頁面自己決定要不要顯示舊資料），失敗時也保留。 */
  data: T | undefined
  /** 目前這一輪載入失敗的訊息；載入中或成功時為 null。 */
  error: string | null
  /** 同一個失敗的 HTTP 狀態與後端錯誤碼（頁面要依 403／404 分流時用）；沒有失敗時為 null。 */
  errorInfo: ApiErrorInfo | null
  /** 目前的依賴還沒有對應的完成結果（首次載入、依賴改變、重試）。 */
  loading: boolean
  /** 以相同依賴重新載入（重試，或寫入動作之後重抓）。 */
  reload: () => void
  /** 直接改寫 data（樂觀更新）；下一次載入完成時以伺服器資料為準。 */
  setData: (value: T | undefined | ((previous: T | undefined) => T | undefined)) => void
}

interface Settled<T> {
  /** 這筆結果對應的請求鍵（依賴＋重試計數）；null＝由 setData 在首次載入完成前寫入 */
  key: readonly unknown[] | null
  data: T | undefined
  error: string | null
  errorInfo: ApiErrorInfo | null
  /** 這筆結果是伺服器端渲染帶來的（initialData），不是這個元件請求的：第一輪的 effect 看到它就不重複請求 */
  fromInitial?: boolean
}

function sameKey(a: readonly unknown[] | null, b: readonly unknown[]): boolean {
  return a !== null && a.length === b.length && a.every((value, index) => Object.is(value, b[index]))
}

function toMessage(error: unknown, errorMessage: UseAsyncDataOptions['errorMessage']): string {
  const info = getApiErrorInfo(error)
  return typeof errorMessage === 'function' ? errorMessage(info, error) : info.message || errorMessage
}

export function useAsyncData<T>(
  fetcher: (signal: AbortSignal) => Promise<T>,
  deps: readonly unknown[],
  options: UseAsyncDataOptions<T>
): UseAsyncDataResult<T> {
  const { enabled = true, errorMessage, onError, initialData } = options
  const [attempt, setAttempt] = useState(0)
  const [settled, setSettled] = useState<Settled<T> | null>(() =>
    initialData === undefined ? null : { key: [...deps, 0], data: initialData, error: null, errorInfo: null, fromInitial: true }
  )
  const key = [...deps, attempt]

  useEffect(() => {
    if (!enabled) return
    const requestKey = key
    // 伺服器端已經帶來這一輪的結果（initialData）：不重複請求。只認「帶來的」那一筆（fromInitial）：
    // 之後任何一次請求完成、或 setData 之後，這個旗標就沒了，依賴再變回同一組也會照常重新請求。
    if (settled?.fromInitial && sameKey(settled.key, requestKey)) return
    const controller = new AbortController()
    fetcher(controller.signal).then(
      (data) => {
        if (controller.signal.aborted) return
        setSettled({ key: requestKey, data, error: null, errorInfo: null })
      },
      (failure: unknown) => {
        // 被我們自己中止（依賴改變、重試、卸載）的請求會以 CanceledError 失敗：那不是錯誤，也不是這一輪的結果
        if (controller.signal.aborted) return
        const message = toMessage(failure, errorMessage)
        setSettled((previous) => ({ key: requestKey, data: previous?.data, error: message, errorInfo: getApiErrorInfo(failure) }))
        onError?.(message, failure)
      }
    )
    return () => controller.abort()
    // 依賴是呼叫端傳入的陣列（展開後長度固定但 lint 看不出來）；呼叫端的依賴由 additionalHooks 設定檢查。
    // fetcher、errorMessage 刻意不列入：它們每次 render 都是新的函式，列入會每次 render 重抓；
    // 它們用到的值必須出現在呼叫端的 deps（lint 會檢查），所以 effect 重跑時拿到的一定是最新的閉包。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...key, enabled])

  const current = settled !== null && sameKey(settled.key, key) ? settled : null
  const loading = enabled && current === null

  const reload = useCallback(() => {
    setAttempt((count) => count + 1)
  }, [])

  const setData = useCallback((value: T | undefined | ((previous: T | undefined) => T | undefined)) => {
    setSettled((previous) => ({
      key: previous?.key ?? null,
      data: typeof value === 'function' ? (value as (p: T | undefined) => T | undefined)(previous?.data) : value,
      error: previous?.error ?? null,
      errorInfo: previous?.errorInfo ?? null,
    }))
  }, [])

  return {
    data: settled?.data,
    error: current?.error ?? null,
    errorInfo: current?.errorInfo ?? null,
    loading,
    reload,
    setData,
  }
}
