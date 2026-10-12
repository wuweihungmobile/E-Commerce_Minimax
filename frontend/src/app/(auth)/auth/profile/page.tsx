'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import AuthService, { getApiErrorInfo, type UserInfoResponse } from '@/services/auth'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { StorefrontShell } from '@/components/layout/StorefrontShell'

// PRD §10.1.1 `/auth/profile`（DEF-358(b)，Sprint 257）：個人資料編輯表單。
// 範圍僅限姓名/電話——email 變更牽涉重新驗證流程，角色/租戶/狀態等欄位屬系統管理，
// 皆不在此表單範圍內（見 SPRINT_257_PLAN.md）。
export default function ProfilePage() {
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [profile, setProfile] = useState<UserInfoResponse | null>(null)

  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saveSuccess, setSaveSuccess] = useState(false)

  useEffect(() => {
    AuthService.fetchCurrentUser()
      .then((data) => {
        setProfile(data)
        setFullName(data.profile.displayName ?? '')
        setPhone(data.profile.phone ?? '')
      })
      .catch((err) => {
        setLoadError(getApiErrorInfo(err).message || '載入個人資料失敗，請稍後再試')
      })
      .finally(() => setLoading(false))
  }, [])

  const handleSave = async () => {
    setSaving(true)
    setSaveError(null)
    setSaveSuccess(false)
    try {
      const updated = await AuthService.updateProfile({ fullName, phone })
      setProfile(updated)
      setFullName(updated.profile.displayName ?? '')
      setPhone(updated.profile.phone ?? '')
      setSaveSuccess(true)
    } catch (err) {
      setSaveError(getApiErrorInfo(err).message || '更新個人資料失敗，請稍後再試')
    } finally {
      setSaving(false)
    }
  }

  return (
    <StorefrontShell>
      <div className="max-w-2xl space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">個人資料</h1>
          <Link href="/account" className="text-sm text-blue-600 hover:underline">
            返回帳戶設定
          </Link>
        </div>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">編輯個人資料</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {loading ? (
              <p className="text-sm text-gray-500" data-testid="profile-loading">
                載入中…
              </p>
            ) : loadError ? (
              <Alert variant="destructive">
                <AlertDescription>{loadError}</AlertDescription>
              </Alert>
            ) : (
              <>
                {profile && (
                  <p className="text-sm text-gray-500">{profile.email}</p>
                )}
                {saveError && (
                  <Alert variant="destructive">
                    <AlertDescription>{saveError}</AlertDescription>
                  </Alert>
                )}
                {saveSuccess && (
                  <Alert variant="success">
                    <AlertDescription data-testid="profile-save-success">已成功更新個人資料</AlertDescription>
                  </Alert>
                )}
                <div>
                  <Label className="mb-1 block">姓名</Label>
                  <Input
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                    data-testid="profile-full-name"
                  />
                </div>
                <div>
                  <Label className="mb-1 block">電話</Label>
                  <Input
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="0912345678"
                    data-testid="profile-phone"
                  />
                </div>
                <Button onClick={handleSave} disabled={saving} data-testid="profile-save">
                  {saving ? '儲存中…' : '儲存'}
                </Button>
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </StorefrontShell>
  )
}
