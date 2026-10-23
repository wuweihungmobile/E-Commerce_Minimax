"use client"

import { useEffect, useSyncExternalStore } from "react"
import { usePathname, useRouter } from "next/navigation"
import { isAuthFlowPath, loginHref } from "@/lib/auth-redirect"
import {
  getAuthenticatedServerSnapshot,
  getAuthenticatedSnapshot,
  subscribeAuth,
} from "@/services/authStore"

// 買家頁面共用的登入守門（DEF-413，Sprint 302，使用者拍板）：`(auth)` 路由群組裡除了登入、註冊、忘記密碼、重設密碼、
// Email 驗證這些認證流程頁面之外，都需要登入（含 /checkout）。沒有 token 就整頁導去 /login?redirect=<原頁>（與 /cart
// 一致），登入後回原頁。沒登入的人不渲染頁面內容——也就不會發出必然 401 的請求、不會閃出「無法載入…請稍後再試」。
//
// 兩個刻意的設計，改的時候別破壞：
// 1. 只在「掛載」與「路徑改變」時檢查，不對登入狀態的變化做反應。登出時 token 被清掉；守門若跟著導向，就會與登出
//    處理函式自己的 router.push('/login') 搶，把網址變成 /login?redirect=…（登出是離開，不該帶原頁）。
// 2. 檢查時直接讀當下的 token（getAuthenticatedSnapshot），不用 render 拿到的 authenticated：整頁重新載入時
//    hydration 當下的快照一律是「未登入」（getServerSnapshot），拿它判斷會把已登入的人誤踢去登入頁。
export function AuthGuard({ children }: { children: React.ReactNode }) {
  const pathname = usePathname() ?? ""
  const router = useRouter()
  const authenticated = useSyncExternalStore(
    subscribeAuth,
    getAuthenticatedSnapshot,
    getAuthenticatedServerSnapshot
  )

  useEffect(() => {
    if (isAuthFlowPath(pathname)) return
    if (!getAuthenticatedSnapshot()) router.replace(loginHref())
  }, [pathname, router])

  if (!isAuthFlowPath(pathname) && !authenticated) return null
  return <>{children}</>
}
