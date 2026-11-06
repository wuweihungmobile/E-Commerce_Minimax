'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import ProductList from '@/components/product/ProductList'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'
import { PageTitle, PageDescription } from '@/components/ui/page-title'

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
            <PageTitle>商品管理</PageTitle>
            <PageDescription>
              管理您的商品資料，上下架商品
            </PageDescription>
          </div>
          <ProductList />
        </div>
      </main>
    </div>
  )
}