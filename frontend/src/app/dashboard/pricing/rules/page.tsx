import type { Metadata } from 'next'
import PricingRuleList from '@/components/pricing/PricingRuleList'
import { PageTitle, PageDescription } from '@/components/ui/page-title'

export const metadata: Metadata = {
  title: '定價規則管理',
}

export default function PricingRulesPage() {
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