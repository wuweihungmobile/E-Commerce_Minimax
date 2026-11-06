// 設計稿 rs-input 規格——所有「欄位」類元件（Input／Textarea／NativeSelect／SelectTrigger）共用的單一來源。
// 單行欄位固定高 44（h-11；設計稿「最小高 44」）、padding 8×16、1px 邊框、radius-md、內文字級（16px）；
// 聚焦＝主色邊框（外框由 globals.css 的 :focus-visible 提供）；aria-invalid＝error 色邊框與外框；停用＝disabled 底與字；無陰影。
// 要改「所有欄位長什麼樣」只改這一個字串。
//
// 為什麼是固定高 h-11，而不是 min-h-11＋padding 撐高：原生 <select> 的高度不按行高撐，Input 會被行高撐成 46px、
// select 卻是 44px，同一排的篩選列對不齊（Sprint 329 實測 44 vs 46；加 leading-6 沒用）。固定高三者必然相等。
// 多行的 Textarea 自己取消固定高（見 textarea.tsx）。
export const fieldStyles =
  "h-11 w-full rounded-md border border-rs-hairline bg-rs-surface px-4 py-2 text-base leading-6 text-rs-ink transition-colors placeholder:text-rs-ink-muted focus-visible:border-rs-primary disabled:cursor-not-allowed disabled:bg-rs-disabled-bg disabled:text-rs-disabled-text aria-invalid:border-rs-error aria-invalid:focus-visible:outline-rs-error"
