import type { Metadata } from 'next'
import { TITLE_TEMPLATE } from '@/lib/site'

// 頁面標題（DEF-465）：這一頁是 client 元件（有 'use client' 的頁面不能匯出 metadata），標題放在同目錄的 layout。
// 這個 layout 底下若還有別的頁面（子路由），它們的標題要靠 template 組成「頁名｜…｜意象若水 RUOSHUI」；
// Next 的規則是中間層的標題如果沒有 template，會把上層傳下來的 template 切斷，所以這裡要重新宣告所屬區域的 template。
export const metadata: Metadata = {
  title: { default: '退貨申請詳情', template: TITLE_TEMPLATE },
}

export default function Layout({ children }: { children: React.ReactNode }) {
  return children
}
