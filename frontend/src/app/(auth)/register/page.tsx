'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import AuthService from '@/services/auth'

export default function RegisterPage() {
  const router = useRouter()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [fullName, setFullName] = useState('')
  const [error, setError] = useState('')
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

    // Password must contain uppercase, lowercase and number
    const hasUpperCase = /[A-Z]/.test(password)
    const hasLowerCase = /[a-z]/.test(password)
    const hasNumber = /\d/.test(password)
    if (!hasUpperCase || !hasLowerCase || !hasNumber) {
      setError('Password must contain uppercase, lowercase and numeric characters')
      return
    }

    setLoading(true)

    try {
      console.log('Starting registration for:', email)
      const result = await AuthService.register({ email, password, fullName })
      console.log('Registration success:', result)
      router.push('/login?registered=true')
    } catch (err: unknown) {
      console.error('Registration error:', err)
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { data?: { message?: string } } }
        setError(axiosErr.response?.data?.message || 'Registration failed')
      } else if (err && typeof err === 'object' && 'message' in err) {
        const msgErr = err as { message?: string }
        setError(msgErr.message || 'An unexpected error occurred')
      } else {
        setError(String(err))
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex-1 flex items-center justify-center px-4 py-12">
      <div className="max-w-md w-full space-y-8 p-8 bg-white rounded-lg border border-rs-hairline">
        <div>
          <h2 className="text-center text-3xl font-semibold text-rs-ink">
            Create your account
          </h2>
          <p className="mt-2 text-center text-sm text-rs-ink-muted">
            Already have an account?{' '}
            <Link href="/login" className="font-medium text-rs-primary hover:text-rs-primary">
              Sign in
            </Link>
          </p>
        </div>

        <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
          {error && (
            <div className="bg-rs-error/10 border border-rs-error/30 text-rs-error px-4 py-3 rounded">
              {error}
            </div>
          )}

          <div className="space-y-4">
            <div>
              <label htmlFor="fullName" className="block text-sm font-medium text-rs-ink">
                Full Name
              </label>
              <input
                id="fullName"
                name="fullName"
                type="text"
                autoComplete="name"
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                className="mt-1 block w-full px-3 py-2 border border-rs-hairline rounded-md focus:outline-none focus:ring-rs-primary focus:border-rs-primary placeholder:text-rs-ink-muted"
                placeholder="John Doe"
                suppressHydrationWarning
              />
            </div>

            <div>
              <label htmlFor="email" className="block text-sm font-medium text-rs-ink">
                Email address
              </label>
              <input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="mt-1 block w-full px-3 py-2 border border-rs-hairline rounded-md focus:outline-none focus:ring-rs-primary focus:border-rs-primary placeholder:text-rs-ink-muted"
                placeholder="you@example.com"
                suppressHydrationWarning
              />
            </div>

            <div>
              <label htmlFor="password" className="block text-sm font-medium text-rs-ink">
                Password
              </label>
              <input
                id="password"
                name="password"
                type="password"
                autoComplete="new-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="mt-1 block w-full px-3 py-2 border border-rs-hairline rounded-md focus:outline-none focus:ring-rs-primary focus:border-rs-primary placeholder:text-rs-ink-muted"
                placeholder="••••••••"
                suppressHydrationWarning
              />
            </div>

            <div>
              <label htmlFor="confirmPassword" className="block text-sm font-medium text-rs-ink">
                Confirm Password
              </label>
              <input
                id="confirmPassword"
                name="confirmPassword"
                type="password"
                autoComplete="new-password"
                required
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                className="mt-1 block w-full px-3 py-2 border border-rs-hairline rounded-md focus:outline-none focus:ring-rs-primary focus:border-rs-primary placeholder:text-rs-ink-muted"
                placeholder="••••••••"
                suppressHydrationWarning
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md text-sm font-medium text-white bg-rs-primary hover:bg-rs-primary-hover focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-rs-primary disabled:opacity-50"
          >
            {loading ? 'Creating account...' : 'Create account'}
          </button>
        </form>
      </div>
    </div>
  )
}
