'use client'

import { useSyncExternalStore } from 'react'
import AuthService from '@/services/auth'

// DEF-361：直接在渲染中呼叫 AuthService.getCurrentUser()（讀 localStorage）會讓 SSR 輸出
// （window 不存在，回傳 null）與客戶端 hydration 當下的渲染（真實 email）不一致，觸發 React
// Hydration Error #418。改用 useSyncExternalStore（比照既有 themeStore.ts／ThemeSwitcher.tsx
// 的慣例）：SSR 與 hydration 當下皆回傳 getServerSnapshot 的 ''，掛載後才切換為 getSnapshot
// 的真實值，該切換不是 hydration 比對的一部分，也不需要在 effect 內同步呼叫 setState。
function subscribe(): () => void {
  return () => {}
}

function getSnapshot(): string {
  return AuthService.getCurrentUser()?.email ?? ''
}

function getServerSnapshot(): string {
  return ''
}

export function useCurrentUserEmail(): string {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot)
}
