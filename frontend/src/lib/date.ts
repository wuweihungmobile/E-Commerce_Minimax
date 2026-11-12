// toISOString() 回傳 UTC 日期；用於「今天」這類本地日曆日概念時，在 UTC+8 時區每天
// 00:00~07:59 會算成前一天，使後端 LocalDate.now()（系統/JVM 時區）比對誤判為過去日期（DEF-362），
// 也讓營收報表的預設結束日少了當天（DEF-405）。要「本地的 YYYY-MM-DD」一律用這個函式。
export function toLocalDateString(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

/**
 * 公開頁（伺服器端渲染，SEO，DEF-500，Sprint 341）顯示日期用：一律用台灣時區（Asia/Taipei）。
 *
 * 為什麼不能用 toLocaleDateString('zh-TW')（沒指定時區）：它用「執行環境的時區」。公開頁的內容現在由伺服器渲染（容器通常是 UTC），
 * 之後在訪客的瀏覽器（台灣是 UTC+8）hydration——同一個時間點，伺服器算出 11/1、瀏覽器算出 11/2（UTC 16:00～24:00 之間發布的文章，
 * 一天有八個小時），React 偵測到文字不符會丟掉伺服器的 HTML 重新渲染（console 出現 hydration 錯誤，搜尋引擎看到的與使用者看到的也不同）。
 * 固定時區之後伺服器與瀏覽器算出同一個字串，而且對所有訪客顯示同一個日期（這是台灣市場的網站；與 DEF-405 以台灣日曆日判斷「今日」一致）。
 */
export function formatPublicDate(iso: string): string {
  return new Date(iso).toLocaleDateString('zh-TW', { timeZone: 'Asia/Taipei' })
}
