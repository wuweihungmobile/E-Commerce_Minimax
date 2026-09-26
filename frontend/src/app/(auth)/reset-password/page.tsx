'use client'

import { Suspense, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import Link from 'next/link'
import AuthService, { getApiErrorInfo } from '@/services/auth'

// Sprint 204（FRD US-M03-006，DEF-253）：密碼規則與註冊頁相同（BR-M03-002）。
// 格式不合時後端在消耗連結之前就會拒絕，所以使用者改好密碼可以直接再送，不必重新申請連結。
function ResetPasswordForm() {
  const searchParams = useSearchParams()
  const token = searchParams.get('token') ?? ''

  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [linkInvalid, setLinkInvalid] = useState(false)
  const [done, setDone] = useState(false)
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (password !== confirmPassword) {
      setError('Passwords do not match')
      return
    }
    if (password.length < 8) {
      setError('Password must be at least 8 characters')
      return
    }
    if (!/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/\d/.test(password)) {
      setError('Password must contain uppercase, lowercase and numeric characters')
      return
    }

    setLoading(true)
    try {
      await AuthService.resetPassword(token, password)
      setDone(true)
    } catch (err: unknown) {
      const { status, code } = getApiErrorInfo(err)
      if (code === 'E-1011') {
        setLinkInvalid(true)
      } else if (status === 429) {
        setError('Too many requests. Please wait a moment and try again.')
      } else {
        setError('Could not reset your password. Please try again.')
      }
    } finally {
      setLoading(false)
    }
  }

  if (done) {
    return (
      <div className="space-y-6" data-testid="reset-password-done">
        <div className="bg-green-50 border border-green-200 text-green-700 px-4 py-3 rounded">
          Your password has been reset. You have been signed out on all devices.
        </div>
        <Link href="/login" className="block text-center font-medium text-blue-600 hover:text-blue-500">
          Sign in with your new password
        </Link>
      </div>
    )
  }

  if (!token || linkInvalid) {
    return (
      <div className="space-y-6" data-testid="reset-password-invalid-link">
        <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded">
          This reset link is invalid or has expired. Links can only be used once and expire after 30 minutes.
        </div>
        <Link href="/forgot-password" className="block text-center font-medium text-blue-600 hover:text-blue-500">
          Request a new link
        </Link>
      </div>
    )
  }

  return (
    <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
      {error && <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded">{error}</div>}

      <div className="space-y-4">
        <div>
          <label htmlFor="password" className="block text-sm font-medium text-gray-700">
            New password
          </label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="new-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 placeholder:text-gray-500"
            placeholder="••••••••"
            suppressHydrationWarning
          />
        </div>
        <div>
          <label htmlFor="confirmPassword" className="block text-sm font-medium text-gray-700">
            Confirm new password
          </label>
          <input
            id="confirmPassword"
            name="confirmPassword"
            type="password"
            autoComplete="new-password"
            required
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 placeholder:text-gray-500"
            placeholder="••••••••"
            suppressHydrationWarning
          />
        </div>
        <p className="text-xs text-gray-500">At least 8 characters, with uppercase, lowercase and a number.</p>
      </div>

      <button
        type="submit"
        disabled={loading}
        className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50"
      >
        {loading ? 'Saving...' : 'Set new password'}
      </button>
    </form>
  )
}

export default function ResetPasswordPage() {
  return (
    <div className="flex-1 flex items-center justify-center px-4 py-12">
      <div className="max-w-md w-full space-y-8 p-8 bg-white rounded-lg shadow-md">
        <h2 className="text-center text-3xl font-bold text-gray-900">Choose a new password</h2>
        <Suspense fallback={null}>
          <ResetPasswordForm />
        </Suspense>
      </div>
    </div>
  )
}
