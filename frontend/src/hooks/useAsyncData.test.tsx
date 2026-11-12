import { StrictMode } from 'react'
import { act, renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { useAsyncData } from './useAsyncData'

// useAsyncData 取代約 70 個頁面各自手寫的「載入中 → 成功／失敗 → 重試」。這些測試守的是頁面曾經（或可能）踩到的缺陷：
// 篩選條件連續改變時先送出的請求較晚回來蓋掉新結果、元件卸載後才回來的回應、重試時畫面閃回舊狀態……
// 每個案例的名稱就是它保護的使用者可見行為。

interface Deferred<T> {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (error: unknown) => void
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

const serverError = (status: number, message?: string) => ({ response: { status, data: message ? { message } : {} } })

describe('useAsyncData：載入', () => {
  it('首次載入：loading 為 true、沒有資料；完成後有資料、loading 結束、沒有錯誤', async () => {
    const request = deferred<string>()
    const { result } = renderHook(() => useAsyncData(() => request.promise, [], { errorMessage: '載入失敗' }))

    expect(result.current).toMatchObject({ loading: true, data: undefined, error: null })

    await act(async () => {
      request.resolve('店鋪資料')
    })
    expect(result.current).toMatchObject({ loading: false, data: '店鋪資料', error: null })
  })

  it('依賴沒變的重新 render 不會重抓（避免每次 render 都打一次 API）', async () => {
    const fetcher = vi.fn().mockResolvedValue('x')
    const { rerender } = renderHook(({ id }) => useAsyncData(fetcher, [id], { errorMessage: 'e' }), { initialProps: { id: 'a' } })
    await act(async () => {})
    rerender({ id: 'a' })
    rerender({ id: 'a' })
    expect(fetcher).toHaveBeenCalledTimes(1)
  })

  it('enabled=false 不發請求且不是 loading（例如還沒拿到路由參數）；變成 true 才發', async () => {
    const fetcher = vi.fn().mockResolvedValue('ok')
    const { result, rerender } = renderHook(({ enabled }) => useAsyncData(fetcher, [], { errorMessage: 'e', enabled }), {
      initialProps: { enabled: false },
    })
    expect(fetcher).not.toHaveBeenCalled()
    expect(result.current.loading).toBe(false)

    rerender({ enabled: true })
    expect(result.current.loading).toBe(true)
    await act(async () => {})
    expect(fetcher).toHaveBeenCalledTimes(1)
    expect(result.current).toMatchObject({ loading: false, data: 'ok' })
  })
})

describe('useAsyncData：失敗', () => {
  it('後端有訊息就顯示後端的訊息', async () => {
    const { result } = renderHook(() =>
      useAsyncData(() => Promise.reject(serverError(403, '您沒有權限')), [], { errorMessage: '載入失敗' })
    )
    await act(async () => {})
    expect(result.current).toMatchObject({ loading: false, error: '您沒有權限', data: undefined })
  })

  it('沒有後端訊息（斷線、程式錯誤）顯示後備文案', async () => {
    const { result } = renderHook(() => useAsyncData(() => Promise.reject(new Error('Network Error')), [], { errorMessage: '載入失敗' }))
    await act(async () => {})
    expect(result.current.error).toBe('載入失敗')
  })

  it('errorMessage 可以是函式，讓頁面依 HTTP 狀態分流（404 顯示「找不到」）', async () => {
    const message = (info: { status?: number; message?: string }) => (info.status === 404 ? '找不到此店鋪' : info.message || '載入失敗')
    const notFound = renderHook(() => useAsyncData(() => Promise.reject(serverError(404, 'Not Found')), [], { errorMessage: message }))
    const other = renderHook(() => useAsyncData(() => Promise.reject(serverError(500, '伺服器忙碌')), [], { errorMessage: message }))
    await act(async () => {})
    expect(notFound.result.current.error).toBe('找不到此店鋪')
    expect(other.result.current.error).toBe('伺服器忙碌')
  })

  it('失敗時保留上一次成功的資料（頁面可以選擇繼續顯示）', async () => {
    const calls = [deferred<string>(), deferred<string>()]
    let call = 0
    const { result } = renderHook(() => useAsyncData(() => calls[call++].promise, [], { errorMessage: '重新整理失敗' }))
    await act(async () => {
      calls[0].resolve('舊資料')
    })
    act(() => result.current.reload())
    await act(async () => {
      calls[1].reject(new Error('boom'))
    })
    expect(result.current).toMatchObject({ data: '舊資料', error: '重新整理失敗', loading: false })
  })
})

describe('useAsyncData：重試與依賴改變', () => {
  it('reload：同一個 render 就回到 loading、錯誤清掉、舊資料保留（畫面不閃空白也不卡在錯誤）', async () => {
    const first = deferred<string>()
    const second = deferred<string>()
    const fetcher = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const { result } = renderHook(() => useAsyncData(fetcher, [], { errorMessage: '載入失敗' }))

    await act(async () => {
      first.reject(new Error('boom'))
    })
    expect(result.current).toMatchObject({ loading: false, error: '載入失敗' })

    act(() => result.current.reload())
    expect(result.current).toMatchObject({ loading: true, error: null })

    await act(async () => {
      second.resolve('第二次成功')
    })
    expect(result.current).toMatchObject({ loading: false, error: null, data: '第二次成功' })
    expect(fetcher).toHaveBeenCalledTimes(2)
  })

  it('依賴改變：該次 render 就是 loading（沒有「舊資料配 loading=false」的一格），舊資料保留到新資料到來', async () => {
    const byId: Record<string, Deferred<string>> = { a: deferred(), b: deferred() }
    const { result, rerender } = renderHook(({ id }) => useAsyncData(() => byId[id].promise, [id], { errorMessage: 'e' }), {
      initialProps: { id: 'a' },
    })
    await act(async () => {
      byId.a.resolve('資料 A')
    })
    expect(result.current).toMatchObject({ loading: false, data: '資料 A' })

    rerender({ id: 'b' })
    expect(result.current).toMatchObject({ loading: true, data: '資料 A' })

    await act(async () => {
      byId.b.resolve('資料 B')
    })
    expect(result.current).toMatchObject({ loading: false, data: '資料 B' })
  })
})

describe('useAsyncData：競態與取消', () => {
  it('先送出的請求較晚回來，不會蓋掉後送出的結果（連續改篩選條件）', async () => {
    const byId: Record<string, Deferred<string>> = { a: deferred(), b: deferred() }
    const { result, rerender } = renderHook(({ id }) => useAsyncData(() => byId[id].promise, [id], { errorMessage: 'e' }), {
      initialProps: { id: 'a' },
    })
    rerender({ id: 'b' })

    await act(async () => {
      byId.b.resolve('資料 B')
    })
    await act(async () => {
      byId.a.resolve('資料 A（太晚了）')
    })
    expect(result.current).toMatchObject({ loading: false, data: '資料 B', error: null })
  })

  it('先送出的請求較晚「失敗」，也不會蓋掉後送出的成功結果', async () => {
    const byId: Record<string, Deferred<string>> = { a: deferred(), b: deferred() }
    const { result, rerender } = renderHook(({ id }) => useAsyncData(() => byId[id].promise, [id], { errorMessage: '載入失敗' }), {
      initialProps: { id: 'a' },
    })
    rerender({ id: 'b' })
    await act(async () => {
      byId.b.resolve('資料 B')
    })
    await act(async () => {
      byId.a.reject(new Error('舊請求失敗'))
    })
    expect(result.current).toMatchObject({ data: '資料 B', error: null, loading: false })
  })

  it('重試時，還在路上的舊請求的回應會被丟棄', async () => {
    const first = deferred<string>()
    const second = deferred<string>()
    const fetcher = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const { result } = renderHook(() => useAsyncData(fetcher, [], { errorMessage: 'e' }))
    act(() => result.current.reload())

    await act(async () => {
      second.resolve('新')
    })
    await act(async () => {
      first.resolve('舊')
    })
    expect(result.current.data).toBe('新')
  })

  it('依賴改變時中止前一個請求（signal.aborted），卸載時中止最後一個', async () => {
    const signals: AbortSignal[] = []
    const fetcher = (signal: AbortSignal) => {
      signals.push(signal)
      return new Promise<string>(() => {})
    }
    const { rerender, unmount } = renderHook(({ id }) => useAsyncData(fetcher, [id], { errorMessage: 'e' }), { initialProps: { id: 'a' } })
    expect(signals).toHaveLength(1)
    expect(signals[0].aborted).toBe(false)

    rerender({ id: 'b' })
    expect(signals).toHaveLength(2)
    expect(signals[0].aborted).toBe(true)
    expect(signals[1].aborted).toBe(false)

    unmount()
    expect(signals[1].aborted).toBe(true)
  })

  it('被我們自己中止的請求（axios 以 CanceledError 失敗）不算錯誤', async () => {
    const byId: Record<string, Deferred<string>> = { a: deferred(), b: deferred() }
    const fetcher = (id: string, signal: AbortSignal) => {
      // axios 中止請求時的錯誤：name 'CanceledError'、code 'ERR_CANCELED'
      signal.addEventListener('abort', () => byId[id].reject(Object.assign(new Error('canceled'), { name: 'CanceledError', code: 'ERR_CANCELED' })))
      return byId[id].promise
    }
    const { result, rerender } = renderHook(({ id }) => useAsyncData((signal) => fetcher(id, signal), [id], { errorMessage: '載入失敗' }), {
      initialProps: { id: 'a' },
    })
    rerender({ id: 'b' }) // 中止 a → a 以 CanceledError 失敗
    await act(async () => {
      byId.b.resolve('資料 B')
    })
    expect(result.current).toMatchObject({ error: null, data: '資料 B', loading: false })
  })

  it('卸載後才回來的回應不會讓 hook 出錯（也不會寫 state）', async () => {
    const request = deferred<string>()
    const { unmount } = renderHook(() => useAsyncData(() => request.promise, [], { errorMessage: 'e' }))
    unmount()
    await expect(
      act(async () => {
        request.resolve('太晚了')
      })
    ).resolves.not.toThrow()
  })
})

describe('useAsyncData：setData（樂觀更新）', () => {
  it('可以直接寫入或用函式更新現有資料，不影響 loading／error', async () => {
    const { result } = renderHook(() => useAsyncData(() => Promise.resolve({ items: [1, 2] }), [], { errorMessage: 'e' }))
    await act(async () => {})
    act(() => result.current.setData((previous) => ({ items: [...(previous?.items ?? []), 3] })))
    expect(result.current).toMatchObject({ data: { items: [1, 2, 3] }, loading: false, error: null })

    act(() => result.current.setData({ items: [] }))
    expect(result.current.data).toEqual({ items: [] })
  })

  it('之後重新載入，以伺服器資料為準（樂觀更新被覆蓋）', async () => {
    let version = 0
    const { result } = renderHook(() => useAsyncData(async () => ({ version: ++version }), [], { errorMessage: 'e' }))
    await act(async () => {})
    act(() => result.current.setData({ version: 999 }))
    act(() => result.current.reload())
    await act(async () => {})
    expect(result.current.data).toEqual({ version: 2 })
  })
})

describe('useAsyncData：onError 與 errorInfo', () => {
  it('onError：這一輪載入失敗時呼叫一次，帶著要顯示的訊息與原始錯誤（給「失敗只用 toast 通知」的頁面）', async () => {
    const onError = vi.fn()
    const failure = serverError(500, '伺服器忙碌')
    renderHook(() => useAsyncData(() => Promise.reject(failure), [], { errorMessage: '載入失敗', onError }))
    await act(async () => {})
    expect(onError).toHaveBeenCalledTimes(1)
    expect(onError).toHaveBeenCalledWith('伺服器忙碌', failure)
  })

  it('onError：成功不呼叫；被中止或已過期的請求失敗也不呼叫（使用者看不到的失敗不該跳通知）', async () => {
    const onError = vi.fn()
    const byId: Record<string, Deferred<string>> = { a: deferred(), b: deferred() }
    const { rerender } = renderHook(({ id }) => useAsyncData(() => byId[id].promise, [id], { errorMessage: '載入失敗', onError }), {
      initialProps: { id: 'a' },
    })
    rerender({ id: 'b' })
    await act(async () => {
      byId.b.resolve('資料 B')
    })
    await act(async () => {
      byId.a.reject(new Error('舊請求失敗'))
    })
    expect(onError).not.toHaveBeenCalled()
  })

  it('errorInfo：失敗時有 HTTP 狀態與後端錯誤碼（頁面依 403 顯示「沒有權限」這類分流）；重試中與成功後回到 null', async () => {
    const first = deferred<string>()
    const second = deferred<string>()
    const fetcher = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const { result } = renderHook(() => useAsyncData(fetcher, [], { errorMessage: '載入失敗' }))
    expect(result.current.errorInfo).toBeNull()

    await act(async () => {
      first.reject({ response: { status: 403, data: { code: 'E-1003', message: '沒有權限' } } })
    })
    expect(result.current.errorInfo).toEqual({ status: 403, code: 'E-1003', message: '沒有權限' })

    act(() => result.current.reload())
    expect(result.current.errorInfo).toBeNull()

    await act(async () => {
      second.resolve('ok')
    })
    expect(result.current.errorInfo).toBeNull()
  })
})

// DEF-500（Sprint 341）：SEO 的伺服器端渲染。伺服器端先取好第一份資料、隨 HTML 一起送出，搜尋引擎與不執行 JavaScript 的爬蟲才看得到內容。
// 這個 hook 要做到的是：有伺服器資料就「第一個畫面就有內容、不再多打一次請求」（否則伺服器端渲染等於白做，後端請求還加倍），
// 沒有（伺服器端取不到）就與先前完全一樣由瀏覽器自己載入。
describe('useAsyncData：伺服器端帶來的初始資料（initialData）', () => {
  it('第一個畫面就有資料、不是 loading，而且完全不發請求（不重抓一次）', async () => {
    const fetcher = vi.fn().mockResolvedValue('瀏覽器抓到的')
    const { result } = renderHook(() => useAsyncData(fetcher, ['p1'], { errorMessage: 'e', initialData: '伺服器帶來的' }))

    expect(result.current).toMatchObject({ loading: false, data: '伺服器帶來的', error: null })
    await act(async () => {})
    expect(fetcher, '伺服器已經取過了；再打一次後端請求等於把伺服器端渲染的好處抵銷').not.toHaveBeenCalled()
    expect(result.current.data).toBe('伺服器帶來的')
  })

  it('React StrictMode（開發模式 effect 會跑兩次）也不會因此多發請求', async () => {
    const fetcher = vi.fn().mockResolvedValue('x')
    renderHook(() => useAsyncData(fetcher, ['p1'], { errorMessage: 'e', initialData: 'ssr' }), { wrapper: StrictMode })
    await act(async () => {})
    expect(fetcher).not.toHaveBeenCalled()
  })

  it('伺服器端沒取到（initialData 是 undefined）→ 與沒有這個選項完全相同：loading、由瀏覽器自己請求', async () => {
    const request = deferred<string>()
    const fetcher = vi.fn().mockReturnValue(request.promise)
    const { result } = renderHook(() => useAsyncData(fetcher, ['p1'], { errorMessage: 'e', initialData: undefined }))

    expect(result.current).toMatchObject({ loading: true, data: undefined })
    expect(fetcher).toHaveBeenCalledTimes(1)
    await act(async () => {
      request.resolve('瀏覽器抓到的')
    })
    expect(result.current).toMatchObject({ loading: false, data: '瀏覽器抓到的' })
  })

  it('依賴改變後照常請求新的一輪（初始資料只對第一輪依賴有效，不會被當成新依賴的結果）', async () => {
    const next = deferred<string>()
    const fetcher = vi.fn().mockReturnValue(next.promise)
    const { result, rerender } = renderHook(({ page }) => useAsyncData(fetcher, [page], { errorMessage: 'e', initialData: '第 1 頁（伺服器）' }), {
      initialProps: { page: 1 },
    })
    expect(result.current.data).toBe('第 1 頁（伺服器）')

    rerender({ page: 2 })
    expect(result.current.loading, '第 2 頁的資料還沒有，不能把第 1 頁的伺服器資料當成它的結果').toBe(true)
    expect(fetcher).toHaveBeenCalledTimes(1)
    await act(async () => {
      next.resolve('第 2 頁')
    })
    expect(result.current).toMatchObject({ loading: false, data: '第 2 頁' })
  })

  it('reload() 照常重新請求（重試、寫入動作後重抓不能被初始資料擋住）', async () => {
    const reloaded = deferred<string>()
    const fetcher = vi.fn().mockReturnValue(reloaded.promise)
    const { result } = renderHook(() => useAsyncData(fetcher, [], { errorMessage: 'e', initialData: '伺服器資料' }))

    act(() => result.current.reload())
    expect(result.current.loading).toBe(true)
    expect(fetcher).toHaveBeenCalledTimes(1)
    await act(async () => {
      reloaded.resolve('重新載入的')
    })
    expect(result.current).toMatchObject({ loading: false, data: '重新載入的' })
  })

  it('依賴換走再換回第一輪的那一組，也會重新請求（初始資料只在第一次掛載時省掉那一次請求）', async () => {
    const fetcher = vi.fn().mockResolvedValue('瀏覽器抓到的')
    const { result, rerender } = renderHook(({ page }) => useAsyncData(fetcher, [page], { errorMessage: 'e', initialData: '伺服器第 1 頁' }), {
      initialProps: { page: 1 },
    })
    rerender({ page: 2 })
    await act(async () => {})
    expect(result.current.data).toBe('瀏覽器抓到的')

    rerender({ page: 1 })
    await act(async () => {})
    expect(fetcher, '第 2 頁之後回到第 1 頁：舊的伺服器資料可能已經過時，要重新請求').toHaveBeenCalledTimes(2)
  })
})
