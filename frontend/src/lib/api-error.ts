// 從 axios 錯誤取出後端 ApiResponse 的 code／message 與 HTTP 狀態。
//
// 原本是 services/auth.ts 裡的 getApiErrorInfo（Sprint 204，三個新頁面與開店申請表單共用）；Sprint 331（DEF-455）搬到 lib/，
// 因為全專案另有 85 處在畫面裡手寫 `err as { response?: { data?: { message?: string } } }`，錯誤處理沒有單一來源，
// 後端回應的形狀一改就要改幾十處。services/auth.ts 仍轉出同名函式，既有 import 不受影響。
export interface ApiFieldError {
  field: string
  message: string
  rejectedValue?: unknown
}

export interface ApiErrorInfo {
  status?: number
  code?: string
  message?: string
  /** 後端驗證失敗時的逐欄位錯誤（ApiResponse.errors），表單頁用它把訊息放到對應欄位旁 */
  fieldErrors?: ApiFieldError[]
}

export function getApiErrorInfo(error: unknown): ApiErrorInfo {
  if (error && typeof error === 'object' && 'response' in error) {
    const response = (error as { response?: { status?: number; data?: { code?: string; message?: string; errors?: ApiFieldError[] } } }).response
    return { status: response?.status, code: response?.data?.code, message: response?.data?.message, fieldErrors: response?.data?.errors }
  }
  return {}
}

/**
 * 要顯示給使用者的錯誤訊息：後端 ApiResponse 的 message 優先（它是寫給使用者看的），沒有就用呼叫端的後備文案。
 * 空字串也視為沒有（與原本畫面裡 `message || fallback` 的行為相同）。
 */
export function apiErrorMessage(error: unknown, fallback: string): string {
  return getApiErrorInfo(error).message || fallback
}
