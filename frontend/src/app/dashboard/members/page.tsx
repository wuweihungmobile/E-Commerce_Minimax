'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import AuthService from '@/services/auth'
import { getApiErrorInfo } from '@/services/auth'
import TenantMemberService, {
  type TenantMemberResponse,
  type MemberCandidateResponse,
  STORE_MEMBER_ROLE_LABELS,
  STORE_MEMBER_STATUS_LABELS,
  storeMemberStatusBadgeVariant,
} from '@/services/tenantMember'

// 邀請／移除共用的錯誤碼對應（DEF-321 (a)，Sprint 248）：後端 BusinessException.getUserMessage()
// 回傳的是 ErrorCode 的罐頭文字，不是拋出時的 details（例如 E-2001 罐頭文字是「租戶未啟用」），
// 直接顯示 error.response.data.message 在這支流程裡會誤導使用者，故依 code 覆寫成貼合情境的文字。
function mapMemberErrorMessage(code: string | undefined, fallback: string): string {
  switch (code) {
    case 'E-2001':
      return '找不到此使用者，請確認 email 是否正確'
    case 'E-2002':
      return '找不到此邀請或成員，可能已被處理，請重新整理頁面'
    case 'E-4092':
      return '此使用者已是成員或已有待確認的邀請'
    case 'E-4031':
      return '無權執行此操作'
    default:
      return fallback
  }
}

function formatJoinedAt(joinedAt: string | null): string {
  return joinedAt ? new Date(joinedAt).toLocaleDateString('zh-TW') : '—'
}

export default function DashboardMembersPage() {
  const router = useRouter()

  const [tenantId, setTenantId] = useState<string | null>(null)
  const [currentUserId, setCurrentUserId] = useState<string | null>(null)
  // 不可直接在 render 期呼叫 AuthService.getCurrentUser()：SSR 階段讀不到 localStorage，
  // 與 client 端首次 render 的結果不一致會觸發 hydration 失敗，React 判定需要在 client 端
  // 整棵子樹重新掛載，連帶把當下輸入到 Email 欄位的使用者輸入一併清空（真實瀏覽器實測）。
  const [currentUserEmail, setCurrentUserEmail] = useState<string | null>(null)
  const [members, setMembers] = useState<TenantMemberResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const [inviteEmail, setInviteEmail] = useState('')
  const [looking, setLooking] = useState(false)
  const [lookupError, setLookupError] = useState<string | null>(null)
  const [candidate, setCandidate] = useState<MemberCandidateResponse | null>(null)
  const [inviting, setInviting] = useState(false)

  const [removingUserId, setRemovingUserId] = useState<string | null>(null)
  const [actionUserId, setActionUserId] = useState<string | null>(null)

  const fetchMembers = useCallback(async (id: string) => {
    setLoading(true)
    setLoadError(null)
    try {
      const result = await TenantMemberService.listMembers(id)
      setMembers(result)
    } catch (err) {
      setLoadError(mapMemberErrorMessage(getApiErrorInfo(err).code, '載入店鋪成員失敗，請稍後再試'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }

    const currentUser = AuthService.getCurrentUser()
    if (currentUser?.role !== 'STORE_OWNER' && currentUser?.role !== 'ADMIN') {
      router.push('/dashboard')
      return
    }

    setCurrentUserId(currentUser.id)
    setCurrentUserEmail(currentUser.email)
    if (currentUser.tenantId) {
      setTenantId(currentUser.tenantId)
    } else {
      setLoading(false)
      setLoadError('找不到店鋪資訊')
    }
  }, [router])

  useEffect(() => {
    if (tenantId) {
      fetchMembers(tenantId)
    }
  }, [tenantId, fetchMembers])

  const handleLookup = async () => {
    if (!tenantId || !inviteEmail.trim()) return
    setLooking(true)
    setLookupError(null)
    setCandidate(null)
    try {
      const found = await TenantMemberService.lookupByEmail(tenantId, inviteEmail.trim())
      setCandidate(found)
    } catch (err) {
      setLookupError(mapMemberErrorMessage(getApiErrorInfo(err).code, '查詢失敗，請稍後再試'))
    } finally {
      setLooking(false)
    }
  }

  const handleCancelCandidate = () => {
    setCandidate(null)
    setLookupError(null)
  }

  const handleInvite = async () => {
    if (!tenantId || !candidate) return
    setInviting(true)
    setActionMessage(null)
    setActionError(null)
    try {
      await TenantMemberService.inviteMember(tenantId, candidate.userId)
      setActionMessage(`已成功邀請 ${candidate.displayName}`)
      setInviteEmail('')
      setCandidate(null)
      await fetchMembers(tenantId)
    } catch (err) {
      setActionError(mapMemberErrorMessage(getApiErrorInfo(err).code, '邀請失敗，請稍後再試'))
    } finally {
      setInviting(false)
    }
  }

  const handleRemove = async (member: TenantMemberResponse) => {
    if (!tenantId) return
    setActionUserId(member.userId)
    setActionMessage(null)
    setActionError(null)
    try {
      await TenantMemberService.removeMember(tenantId, member.userId)
      setActionMessage(
        member.status === 'INVITED' ? `已撤回對 ${member.displayName} 的邀請` : `已移除 ${member.displayName}`
      )
      setRemovingUserId(null)
      await fetchMembers(tenantId)
    } catch (err) {
      setActionError(mapMemberErrorMessage(getApiErrorInfo(err).code, '操作失敗，請稍後再試'))
    } finally {
      setActionUserId(null)
    }
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center gap-4">
              <Link href="/dashboard" className="text-gray-600 hover:text-gray-900">
                Dashboard
              </Link>
              <span className="text-gray-400">/</span>
              <span className="text-gray-900 font-medium">店鋪成員管理</span>
            </div>
            <div className="flex items-center gap-4">
              <span className="text-sm text-gray-600">{currentUserEmail}</span>
              <button
                onClick={async () => {
                  await AuthService.logout()
                  router.push('/login')
                }}
                className="px-3 py-1.5 text-sm text-white bg-red-500 rounded-md hover:bg-red-600"
              >
                登出
              </button>
            </div>
          </div>
        </div>
      </nav>

      <main className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="px-4 py-6 sm:px-0 space-y-6">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">店鋪成員管理</h1>
            <p className="mt-1 text-sm text-gray-600">
              {members.length > 0 ? `共 ${members.length} 位成員` : '目前尚無店員'}
            </p>
          </div>

          {actionMessage && (
            <div data-testid="action-message" className="bg-green-50 border border-green-200 text-green-800 px-4 py-3 rounded-md">
              {actionMessage}
            </div>
          )}
          {actionError && (
            <div data-testid="action-error" className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-md">
              {actionError}
            </div>
          )}

          <Card>
            <CardHeader>
              <CardTitle className="text-base">邀請新成員</CardTitle>
              <CardDescription>以 email 查詢對方帳號後確認邀請，對方接受後才會正式成為店員。</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex gap-3 items-end">
                <div className="flex-1 space-y-2">
                  <Label htmlFor="invite-email">使用者 Email</Label>
                  <Input
                    id="invite-email"
                    data-testid="invite-email-input"
                    type="email"
                    value={inviteEmail}
                    onChange={(e) => {
                      setInviteEmail(e.target.value)
                      setCandidate(null)
                      setLookupError(null)
                    }}
                    placeholder="colleague@example.com"
                  />
                </div>
                <Button
                  data-testid="invite-lookup"
                  variant="outline"
                  onClick={handleLookup}
                  disabled={looking || !inviteEmail.trim()}
                >
                  {looking ? '查詢中...' : '查詢'}
                </Button>
              </div>

              {lookupError && (
                <Alert variant="destructive">
                  <AlertDescription>{lookupError}</AlertDescription>
                </Alert>
              )}

              {candidate && (
                <div className="border rounded-md p-4 space-y-3 bg-gray-50">
                  <p className="text-sm text-gray-700">
                    找到使用者：<span className="font-medium">{candidate.displayName}</span>（{candidate.email}）
                  </p>
                  <div className="flex gap-2">
                    <Button data-testid="invite-confirm" size="sm" onClick={handleInvite} disabled={inviting}>
                      {inviting ? '邀請中...' : '確認邀請'}
                    </Button>
                    <Button variant="outline" size="sm" onClick={handleCancelCandidate} disabled={inviting}>
                      取消
                    </Button>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">成員列表</CardTitle>
            </CardHeader>
            <CardContent>
              {loading ? (
                <p className="text-sm text-gray-600">載入中...</p>
              ) : loadError ? (
                <Alert variant="destructive">
                  <AlertDescription>{loadError}</AlertDescription>
                </Alert>
              ) : members.length === 0 ? (
                <p className="text-sm text-gray-600">目前沒有其他店員，邀請同事加入吧。</p>
              ) : (
                <div className="divide-y">
                  {members.map((member) => {
                    const isOwner = member.role === 'STORE_OWNER'
                    const isSelf = member.userId === currentUserId
                    const canRemove = !isOwner && !isSelf
                    return (
                      <div key={member.userId} className="py-4 flex items-center justify-between gap-4 flex-wrap">
                        <div>
                          <p className="font-medium text-gray-900">{member.displayName || member.email}</p>
                          <p className="text-sm text-gray-600">{member.email}</p>
                        </div>
                        <div className="flex items-center gap-3">
                          <Badge variant="outline">{STORE_MEMBER_ROLE_LABELS[member.role]}</Badge>
                          <Badge variant={storeMemberStatusBadgeVariant(member.status)}>
                            {STORE_MEMBER_STATUS_LABELS[member.status]}
                          </Badge>
                          <span className="text-sm text-gray-500 w-24">{formatJoinedAt(member.joinedAt)}</span>
                          {canRemove &&
                            (removingUserId === member.userId ? (
                              <div className="flex gap-2">
                                <Button
                                  variant="destructive"
                                  size="sm"
                                  onClick={() => handleRemove(member)}
                                  disabled={actionUserId === member.userId}
                                >
                                  {actionUserId === member.userId ? '處理中...' : '確認'}
                                </Button>
                                <Button variant="outline" size="sm" onClick={() => setRemovingUserId(null)}>
                                  取消
                                </Button>
                              </div>
                            ) : (
                              <Button
                                data-testid={`remove-member-${member.userId}`}
                                variant="outline"
                                size="sm"
                                onClick={() => setRemovingUserId(member.userId)}
                              >
                                {member.status === 'INVITED' ? '撤回邀請' : '移除'}
                              </Button>
                            ))}
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </main>
    </div>
  )
}
