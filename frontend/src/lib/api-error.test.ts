import { describe, expect, it } from 'vitest'
import { apiErrorMessage, getApiErrorInfo } from './api-error'

// 畫面上顯示的錯誤訊息怎麼來的：後端 ApiResponse 的 message 是寫給使用者看的，優先使用；
// 沒有（網路斷線、被中止、程式錯誤）就用呼叫端準備的後備文案。Sprint 331 之前這段邏輯在 85 處各寫一次。
const serverError = (status: number, data: Record<string, unknown>) => ({ response: { status, data } })

describe('getApiErrorInfo', () => {
  it('取出 HTTP 狀態、後端錯誤碼與訊息', () => {
    expect(getApiErrorInfo(serverError(409, { code: 'E-2010', message: '店鋪暫停營業' }))).toEqual({
      status: 409,
      code: 'E-2010',
      message: '店鋪暫停營業',
    })
  })

  it('有回應但本文不是 ApiResponse 形狀（例如 502 的 HTML）：只有狀態，沒有 code／message', () => {
    expect(getApiErrorInfo({ response: { status: 502, data: '<html>Bad Gateway</html>' } })).toEqual({
      status: 502,
      code: undefined,
      message: undefined,
      fieldErrors: undefined,
    })
  })

  it('後端驗證失敗（400）帶逐欄位錯誤：fieldErrors 原樣交給表單頁，把訊息放到對應欄位旁', () => {
    const errors = [
      { field: 'title', message: '標題不可為空' },
      { field: 'basePrice', message: '價格必須大於 0', rejectedValue: -1 },
    ]
    expect(getApiErrorInfo(serverError(400, { code: 'E-1001', message: '驗證失敗', errors })).fieldErrors).toEqual(errors)
  })

  it.each([
    ['一般 Error（程式錯誤）', new Error('boom')],
    ['字串', 'oops'],
    ['null', null],
    ['undefined', undefined],
    ['沒有 response 的網路錯誤', { message: 'Network Error', code: 'ERR_NETWORK' }],
  ])('%s → 空物件（不丟例外）', (_label, error) => {
    expect(getApiErrorInfo(error)).toEqual({})
  })
})

describe('apiErrorMessage', () => {
  it('後端有訊息就用後端的（它比任何通用文案更能告訴使用者哪裡錯）', () => {
    expect(apiErrorMessage(serverError(400, { message: '商品庫存不足' }), '載入失敗')).toBe('商品庫存不足')
  })

  it('後端沒給訊息（或給空字串）用後備文案', () => {
    expect(apiErrorMessage(serverError(500, {}), '載入失敗')).toBe('載入失敗')
    expect(apiErrorMessage(serverError(500, { message: '' }), '載入失敗')).toBe('載入失敗')
  })

  it('不是 HTTP 錯誤（斷線、程式錯誤）用後備文案', () => {
    expect(apiErrorMessage(new Error('Network Error'), '載入失敗')).toBe('載入失敗')
    expect(apiErrorMessage(undefined, '載入失敗')).toBe('載入失敗')
  })
})
