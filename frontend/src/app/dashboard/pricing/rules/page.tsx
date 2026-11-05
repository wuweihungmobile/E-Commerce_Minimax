'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import PricingRuleList from '@/components/pricing/PricingRuleList'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

export default function PricingRulesPage() {
  const router = useRouter()

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push(loginHref())
      return
    }
  }, [router])

  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <h1 className="text-2xl font-semibold text-rs-ink">定價規則管理</h1>
            <p className="mt-1 text-sm text-rs-ink-muted">
              設定房源的動態定價規則，管理價格調整策略
            </p>
          </div>
          <PricingRuleList />
        </div>
      </main>
    </div>
  )
}