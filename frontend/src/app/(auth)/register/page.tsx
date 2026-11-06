'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import AuthService from '@/services/auth'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Alert } from '@/components/ui/alert'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { apiErrorMessage } from '@/lib/api-error'

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
        setError(apiErrorMessage(err, 'Registration failed'))
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
      <Card className="max-w-md w-full space-y-8 p-8">
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
            <Alert variant="destructive">
              {error}
            </Alert>
          )}

          <div className="space-y-4">
            <div>
              <Label htmlFor="fullName" className="block">
                Full Name
              </Label>
              <Input
                id="fullName"
                name="fullName"
                type="text"
                autoComplete="name"
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                className="mt-1 block"
                placeholder="John Doe"
                suppressHydrationWarning
              />
            </div>

            <div>
              <Label htmlFor="email" className="block">
                Email address
              </Label>
              <Input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="mt-1 block"
                placeholder="you@example.com"
                suppressHydrationWarning
              />
            </div>

            <div>
              <Label htmlFor="password" className="block">
                Password
              </Label>
              <Input
                id="password"
                name="password"
                type="password"
                autoComplete="new-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="mt-1 block"
                placeholder="••••••••"
                suppressHydrationWarning
              />
            </div>

            <div>
              <Label htmlFor="confirmPassword" className="block">
                Confirm Password
              </Label>
              <Input
                id="confirmPassword"
                name="confirmPassword"
                type="password"
                autoComplete="new-password"
                required
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                className="mt-1 block"
                placeholder="••••••••"
                suppressHydrationWarning
              />
            </div>
          </div>

          <Button
            variant="default"
            type="submit"
            disabled={loading}
            className="w-full"
          >
            {loading ? 'Creating account...' : 'Create account'}
          </Button>
        </form>
      </Card>
    </div>
  )
}
