import AuthService from "./auth"

// 極簡 auth 外部 store：供 `useSyncExternalStore` 讀取登入 email。
// 為何需要：共用 Header 置於 route-group layout，layout 不隨導覽 re-render
// （Next App Router 特性），故登出後需靠 store 訂閱通知 Header 即時更新。
// 快照回傳 primitive（email 字串 / null），比較穩定、不會造成 re-render 迴圈；
// 避免在 effect 內同步 setState（React 19 嚴格 hooks 禁令）。
const AUTH_EVENT = "nextkey:auth-change"

export function subscribeAuth(callback: () => void): () => void {
  if (typeof window === "undefined") return () => {}
  window.addEventListener("storage", callback)
  window.addEventListener(AUTH_EVENT, callback)
  return () => {
    window.removeEventListener("storage", callback)
    window.removeEventListener(AUTH_EVENT, callback)
  }
}

// client 快照：登入回 email，未登入回 null（primitive，供穩定比較）
export function getAuthEmailSnapshot(): string | null {
  if (!AuthService.isAuthenticated()) return null
  return AuthService.getCurrentUser()?.email ?? null
}

// server / hydration 快照：一律 null（SSR 無 localStorage），確保 hydration 無 mismatch
export function getAuthServerSnapshot(): null {
  return null
}

// 登入/登出後呼叫，通知同頁訂閱者（Header）即時更新
export function notifyAuthChange(): void {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(AUTH_EVENT))
  }
}
