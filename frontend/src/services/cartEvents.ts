// 購物車變更事件：加購/移除後通知共用 Header 重新抓取購物車數量。
// 因 Header 置於 route-group layout（不隨導覽 re-render），需以事件主動通知。
const CART_CHANGED_EVENT = "nextkey:cart-changed"

export function subscribeCartChanged(callback: () => void): () => void {
  if (typeof window === "undefined") return () => {}
  window.addEventListener(CART_CHANGED_EVENT, callback)
  return () => window.removeEventListener(CART_CHANGED_EVENT, callback)
}

export function notifyCartChanged(): void {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(CART_CHANGED_EVENT))
  }
}
