'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import PricingRuleList from '@/components/pricing/PricingRuleList'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'
import { PageTitle, PageDescription } from '@/components/ui/page-title'

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
            <PageTitle>定價規則管理</PageTitle>
            <PageDescription>
              設定房源的動態定價規則，管理價格調整策略
            </PageDescription>
          </div>
          <PricingRuleList />
        </div>
      </main>
    </div>
  )
}