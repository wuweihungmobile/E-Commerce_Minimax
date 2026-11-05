'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useParams } from 'next/navigation'
import ProductForm from '@/components/product/ProductForm'
import SkuManager from '@/components/product/SkuManager'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

export default function EditProductPage() {
  const router = useRouter()
  const params = useParams()

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push(loginHref())
      return
    }
  }, [router])

  return (
    <div>
      <main className="max-w-3xl">
        <div className="space-y-6">
          <ProductForm productId={params.id as string} />
          <SkuManager listingId={params.id as string} />
        </div>
      </main>
    </div>
  )
}