// toISOString() 回傳 UTC 日期；用於「今天」這類本地日曆日概念時，在 UTC+8 時區每天
// 00:00~07:59 會算成前一天，使後端 LocalDate.now()（系統/JVM 時區）比對誤判為過去日期（DEF-362），
// 也讓營收報表的預設結束日少了當天（DEF-405）。要「本地的 YYYY-MM-DD」一律用這個函式。
export function toLocalDateString(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
