'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import ShippingTemplateList from '@/components/shipping/ShippingTemplateList'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

export default function ShippingTemplatesPage() {
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
            <h1 className="text-2xl font-semibold text-rs-ink">運費模板設定</h1>
            <p className="mt-1 text-sm text-rs-ink-muted">
              設定固定運費或滿額免運門檻，套用於商品訂單結帳計算
            </p>
          </div>
          <ShippingTemplateList />
        </div>
      </main>
    </div>
  )
}
