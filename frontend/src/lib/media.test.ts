import { describe, expect, it } from 'vitest'
import { absolutePublicMediaUrl, MEDIA_ACCEPT, validateMediaFile } from './media'

// 媒體上傳預檢：規則必須與後端 MediaValidationService 一致（圖片 10 MB、影片 100 MB、文件 5 MB；白名單見該類別）。
// 兩邊不一致的後果：前端放行、後端拒絕 → 使用者只看到泛用的「上傳失敗」；或前端擋掉後端其實收的檔案。
const MB = 1024 * 1024

describe('validateMediaFile', () => {
  it('白名單內的類型、在上限內 → 通過（回 null）', () => {
    for (const type of [
      'image/jpeg',
      'image/png',
      'image/gif',
      'image/webp',
      'video/mp4',
      'video/quicktime',
      'video/x-msvideo',
      'application/pdf',
    ]) {
      expect(validateMediaFile({ type, size: 1024 }), type).toBeNull()
    }
  })

  it('不在白名單的類型 → 擋下並說明可上傳哪些（svg 與 zip 都不收）', () => {
    for (const type of ['image/svg+xml', 'application/zip', 'text/html', '']) {
      expect(validateMediaFile({ type, size: 1024 }), `type=${JSON.stringify(type)}`).toContain('不支援的檔案類型')
    }
  })

  it('各類型的上限：剛好等於上限通過，多 1 byte 擋下，訊息帶出該類型的上限', () => {
    const cases: Array<[string, number, string]> = [
      ['image/png', 10 * MB, '圖片上限 10 MB'],
      ['video/mp4', 100 * MB, '影片上限 100 MB'],
      ['application/pdf', 5 * MB, '文件上限 5 MB'],
    ]
    for (const [type, limit, expected] of cases) {
      expect(validateMediaFile({ type, size: limit }), `${type} 剛好等於上限`).toBeNull()
      expect(validateMediaFile({ type, size: limit + 1 }), `${type} 超過 1 byte`).toContain(expected)
    }
  })

  it('PDF 的上限比圖片小：6 MB 的 PDF 擋下、6 MB 的圖片通過（不能拿同一個上限套所有類型）', () => {
    expect(validateMediaFile({ type: 'application/pdf', size: 6 * MB })).toContain('文件上限 5 MB')
    expect(validateMediaFile({ type: 'image/png', size: 6 * MB })).toBeNull()
  })
})

describe('MEDIA_ACCEPT', () => {
  it('涵蓋白名單的每一個 MIME 類型（<input accept> 與預檢是同一份清單，不會一個放行一個擋）', () => {
    const accepted = MEDIA_ACCEPT.split(',')
    expect(accepted).toHaveLength(8)
    for (const type of accepted) expect(validateMediaFile({ type, size: 1 }), type).toBeNull()
  })
})

describe('absolutePublicMediaUrl（Sprint 336，DEF-482）', () => {
  const PATH = '/api/v2/public/media/11111111-1111-1111-1111-111111111111'

  it('API 基底是絕對網址 → 補上後端的 origin；基底帶不帶 /api、/v2 都不影響（只取 origin）', () => {
    for (const apiBase of ['http://localhost:8080/api', 'http://localhost:8080/api/v2', 'http://localhost:8080']) {
      expect(absolutePublicMediaUrl(PATH, { apiBase, pageOrigin: 'http://localhost:3000' }), apiBase).toBe(
        `http://localhost:8080${PATH}`
      )
    }
  })

  it('前端與後端是不同網域時，指向後端的網域（不是頁面的網域）', () => {
    expect(absolutePublicMediaUrl(PATH, { apiBase: 'https://api.nextkey.com/api', pageOrigin: 'https://www.nextkey.com' })).toBe(
      `https://api.nextkey.com${PATH}`
    )
  })

  it('API 基底不是絕對網址（同源代理的 /api）→ 用目前頁面的 origin', () => {
    expect(absolutePublicMediaUrl(PATH, { apiBase: '/api', pageOrigin: 'https://www.nextkey.com' })).toBe(
      `https://www.nextkey.com${PATH}`
    )
  })

  it('沒有 API 基底也沒有頁面 origin（伺服器端）→ 原樣回傳路徑，不丟例外', () => {
    expect(absolutePublicMediaUrl(PATH, { apiBase: '', pageOrigin: undefined })).toBe(PATH)
  })

  it('設定值不是合法網址 → 退回原樣，不讓複製按鈕壞掉', () => {
    expect(absolutePublicMediaUrl(PATH, { apiBase: 'http://', pageOrigin: undefined })).toBe(PATH)
  })
})
