import { useSyncExternalStore } from 'react'

const subscribe = () => () => {}

/**
 * 伺服器端渲染與 hydration 期間是 false，hydration 完成後是 true。
 *
 * 用在「輸出依賴瀏覽器環境」的區塊（現在的時間、時區、視窗大小）：這類內容在伺服器端算出的結果與訪客瀏覽器不同時，
 * React 會偵測到不符、丟掉伺服器的 HTML 重新渲染。公開頁的內容改由伺服器渲染之後（SEO，DEF-500，Sprint 341），
 * 這類區塊要等到瀏覽器接手之後才渲染。React 官方建議的寫法（useSyncExternalStore 的 server snapshot），
 * 與 services/authStore 的訪客／登入態切換同一個做法——不在 effect 裡同步 setState（React 19 嚴格 hooks 規則）。
 */
export function useIsClient(): boolean {
  return useSyncExternalStore(
    subscribe,
    () => true,
    () => false
  )
}
