import type { Metadata } from 'next'
import { TITLE_TEMPLATE } from '@/lib/site'

// 頁面標題（DEF-465）放在這個 layout：底下的 /blog/[slug]（舊文章網址導向頁，client 元件，不能匯出 metadata）也靠這裡的 template
// 組成「頁名｜…｜意象若水 RUOSHUI」；Next 的規則是中間層的標題如果沒有 template，會把上層傳下來的 template 切斷，
// 所以這裡要重新宣告所屬區域的 template。
export const metadata: Metadata = {
  title: { default: '部落格', template: TITLE_TEMPLATE },
}

export default function Layout({ children }: { children: React.ReactNode }) {
  return children
}
