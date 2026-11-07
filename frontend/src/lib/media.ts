// 媒體上傳的前端預檢（Sprint 332，DEF-466：自已移除的 /cms/media 頁面移植過來）。
//
// 白名單與上限與後端 `MediaValidationService`（M15／M18 兩套媒體 API 共用的驗證服務）一致：
// 圖片 jpeg／png／gif／webp 10 MB、影片 mp4／quicktime／x-msvideo 100 MB、文件 pdf 5 MB。
// 後端仍會再驗（還會檢查檔案內容的 magic bytes），這裡只是讓使用者在選檔當下就得到明確的中文訊息，
// 而不是上傳到一半才拿到「上傳失敗，請稍後再試」。後端規則改了，這裡要一起改（lib/media.test.ts 會把兩邊的數字釘住）。

const MB = 1024 * 1024

interface MediaKind {
  label: string
  types: readonly string[]
  maxBytes: number
}

const KINDS: readonly MediaKind[] = [
  { label: '圖片', types: ['image/jpeg', 'image/png', 'image/gif', 'image/webp'], maxBytes: 10 * MB },
  { label: '影片', types: ['video/mp4', 'video/quicktime', 'video/x-msvideo'], maxBytes: 100 * MB },
  { label: '文件', types: ['application/pdf'], maxBytes: 5 * MB },
]

/** 可上傳的 MIME 類型（給 <input type="file" accept> 用） */
export const MEDIA_ACCEPT = KINDS.flatMap((kind) => kind.types).join(',')

/** 檔案可以上傳就回 null；否則回要顯示給使用者的訊息 */
export function validateMediaFile(file: { type: string; size: number }): string | null {
  const kind = KINDS.find((k) => k.types.includes(file.type))
  if (!kind) return '不支援的檔案類型（可上傳圖片、影片、PDF）'
  if (file.size > kind.maxBytes) return `檔案大小超過限制（${kind.label}上限 ${kind.maxBytes / MB} MB）`
  return null
}
