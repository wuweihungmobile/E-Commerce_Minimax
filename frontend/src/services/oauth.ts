import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'
import type { ApiResponse, AuthResponse } from '@/services/auth'

// Sprint 153（item 13）：後端 OAuthService 既有 stub（Sprint 78 記錄）改為真正可用的
// Authorization Code 交換邏輯（GOOGLE/GITHUB）。本檔負責前端這一半：產生真實 provider
// 授權網址、state CSRF 防護（sessionStorage 產生+比對，SPA 標準作法，後端無須也未持有 state）、
// 呼叫後端登入/連結端點。

export type OAuthProviderId = 'google' | 'github'
export type OAuthIntent = 'login' | 'link'

interface ProviderConfig {
  clientId: string | undefined
  authorizeUrl: string
  scope: string
}

// client-id 屬公開資訊（非 secret），走 NEXT_PUBLIC_* 建置期環境變數；未設定時對應 provider
// 的登入/連結按鈕不會顯示（見 isOAuthProviderConfigured），避免出現一個必然失敗的按鈕。
const PROVIDER_CONFIG: Record<OAuthProviderId, ProviderConfig> = {
  google: {
    clientId: process.env.NEXT_PUBLIC_OAUTH_GOOGLE_CLIENT_ID,
    authorizeUrl: 'https://accounts.google.com/o/oauth2/v2/auth',
    scope: 'openid email profile',
  },
  github: {
    clientId: process.env.NEXT_PUBLIC_OAUTH_GITHUB_CLIENT_ID,
    authorizeUrl: 'https://github.com/login/oauth/authorize',
    scope: 'read:user user:email',
  },
}

const STATE_KEY = 'oauth_state'
const INTENT_KEY = 'oauth_intent'

export function isOAuthProviderConfigured(provider: OAuthProviderId): boolean {
  return Boolean(PROVIDER_CONFIG[provider].clientId)
}

export function oauthRedirectUri(provider: OAuthProviderId): string {
  return `${window.location.origin}/oauth/callback/${provider}`
}

/**
 * 發起 OAuth 授權流程：產生隨機 state 存入 sessionStorage，重導至 provider 真實授權頁面。
 * intent 區分「未登入使用者登入/註冊」與「已登入使用者連結第三方帳號」，callback 頁讀回後
 * 呼叫對應的後端端點。
 */
export function startOAuthFlow(provider: OAuthProviderId, intent: OAuthIntent): void {
  const config = PROVIDER_CONFIG[provider]
  if (!config.clientId) return // 呼叫端應先用 isOAuthProviderConfigured 判斷是否顯示按鈕

  const state = crypto.randomUUID()
  sessionStorage.setItem(STATE_KEY, state)
  sessionStorage.setItem(INTENT_KEY, intent)

  const params = new URLSearchParams({
    client_id: config.clientId,
    redirect_uri: oauthRedirectUri(provider),
    response_type: 'code',
    scope: config.scope,
    state,
  })
  window.location.href = `${config.authorizeUrl}?${params.toString()}`
}

/**
 * callback 頁讀到 provider 帶回的 state 後呼叫本函式驗證，並取回發起時記錄的 intent。
 * 驗證後立即清除 sessionStorage 記錄（一次性使用，防止 state 被重放）。
 */
export function consumeOAuthState(receivedState: string | null): { valid: boolean; intent: OAuthIntent | null } {
  const storedState = sessionStorage.getItem(STATE_KEY)
  const intent = sessionStorage.getItem(INTENT_KEY) as OAuthIntent | null
  sessionStorage.removeItem(STATE_KEY)
  sessionStorage.removeItem(INTENT_KEY)
  return {
    valid: Boolean(storedState) && Boolean(receivedState) && storedState === receivedState,
    intent,
  }
}

class OAuthApiService {
  async login(provider: OAuthProviderId, code: string, redirectUri: string): Promise<AuthResponse> {
    const response = await apiClient.post<ApiResponse<AuthResponse>>(API_ENDPOINTS.auth.oauthLogin, {
      provider: provider.toUpperCase(),
      code,
      redirectUri,
    })
    return response.data.data
  }

  async link(provider: OAuthProviderId, code: string, redirectUri: string): Promise<void> {
    await apiClient.post(API_ENDPOINTS.auth.oauthLink, {
      provider: provider.toUpperCase(),
      code,
      redirectUri,
    })
  }
}

export default new OAuthApiService()
