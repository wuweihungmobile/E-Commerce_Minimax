'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import ProductList from '@/components/product/ProductList'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

export default function ProductsPage() {
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
            <h1 className="text-2xl font-semibold text-rs-ink">商品管理</h1>
            <p className="mt-1 text-sm text-rs-ink-muted">
              管理您的商品資料，上下架商品
            </p>
          </div>
          <ProductList />
        </div>
      </main>
    </div>
  )
}