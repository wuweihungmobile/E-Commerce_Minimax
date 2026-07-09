'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import apiClient from '@/lib/axios'
import { API_ENDPOINTS } from '@/lib/api'
import AuthService from '@/services/auth'

// Tenant status enum
type TenantStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED'

// Tenant interface
interface Tenant {
  id: string
  storeName: string
  businessType: string
  contactEmail: string
  contactPhone: string
  status: TenantStatus
  createdAt: string
  updatedAt: string
  purchaseOrderApprovalThreshold?: number | null
}

interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

interface TenantEditFormProps {
  tenantId: string
}

interface TenantEditFormData {
  storeName: string
  businessType: string
  contactEmail: string
  contactPhone: string
  purchaseOrderApprovalThreshold: string
}

interface FormErrors {
  storeName?: string
  businessType?: string
  contactEmail?: string
  contactPhone?: string
  purchaseOrderApprovalThreshold?: string
}

export default function TenantEditForm({ tenantId }: TenantEditFormProps) {
  const router = useRouter()
  const [tenant, setTenant] = useState<Tenant | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [formData, setFormData] = useState<TenantEditFormData>({
    storeName: '',
    businessType: '',
    contactEmail: '',
    contactPhone: '',
    purchaseOrderApprovalThreshold: '',
  })
  const [errors, setErrors] = useState<FormErrors>({})

  const businessTypes = [
    { value: 'RETAIL', label: '零售' },
    { value: 'WHOLESALE', label: '批發' },
    { value: 'F&B', label: '餐飲' },
    { value: 'SERVICE', label: '服務業' },
    { value: 'MANUFACTURING', label: '製造業' },
    { value: 'OTHER', label: '其他' },
  ]

  useEffect(() => {
    fetchTenantDetail()
  }, [tenantId])

  const fetchTenantDetail = async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await apiClient.get<ApiResponse<Tenant>>(
        API_ENDPOINTS.tenants.detail(tenantId)
      )
      const tenantData = response.data.data
      setTenant(tenantData)
      setFormData({
        storeName: tenantData.storeName,
        businessType: tenantData.businessType,
        contactEmail: tenantData.contactEmail,
        contactPhone: tenantData.contactPhone,
        purchaseOrderApprovalThreshold:
          tenantData.purchaseOrderApprovalThreshold != null
            ? String(tenantData.purchaseOrderApprovalThreshold)
            : '',
      })
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { status?: number; data?: { message?: string } } }
        if (axiosErr.response?.status === 404) {
          setError('找不到此店鋪')
        } else if (axiosErr.response?.status === 403) {
          setError('您沒有權限編輯此店鋪')
        } else {
          setError(axiosErr.response?.data?.message || '載入店鋪失敗')
        }
      } else {
        setError('載入店鋪失敗')
      }
    } finally {
      setLoading(false)
    }
  }

  const validateForm = (): boolean => {
    const newErrors: FormErrors = {}

    // Store name validation
    if (!formData.storeName.trim()) {
      newErrors.storeName = '請輸入店鋪名稱'
    } else if (formData.storeName.length < 2) {
      newErrors.storeName = '店鋪名稱至少需要 2 個字元'
    } else if (formData.storeName.length > 100) {
      newErrors.storeName = '店鋪名稱不能超過 100 個字元'
    }

    // Business type validation
    if (!formData.businessType) {
      newErrors.businessType = '請選擇營業類型'
    }

    // Email validation
    if (!formData.contactEmail.trim()) {
      newErrors.contactEmail = '請輸入聯絡信箱'
    } else {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
      if (!emailRegex.test(formData.contactEmail)) {
        newErrors.contactEmail = '請輸入有效的信箱格式'
      }
    }

    // Phone validation
    if (!formData.contactPhone.trim()) {
      newErrors.contactPhone = '請輸入聯絡電話'
    } else {
      const phoneRegex = /^[\d\-\+\(\)\s]{8,20}$/
      if (!phoneRegex.test(formData.contactPhone)) {
        newErrors.contactPhone = '請輸入有效的電話號碼'
      }
    }

    // Purchase order approval threshold validation（選填，空值＝不設定門檻）
    if (formData.purchaseOrderApprovalThreshold.trim()) {
      const threshold = Number(formData.purchaseOrderApprovalThreshold)
      if (Number.isNaN(threshold) || threshold < 0) {
        newErrors.purchaseOrderApprovalThreshold = '請輸入不小於 0 的數字'
      }
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target
    setFormData((prev) => ({ ...prev, [name]: value }))
    // Clear error when user starts typing
    if (errors[name as keyof FormErrors]) {
      setErrors((prev) => ({ ...prev, [name]: undefined }))
    }
  }

  const handleBusinessTypeChange = (value: string) => {
    setFormData((prev) => ({ ...prev, businessType: value }))
    if (errors.businessType) {
      setErrors((prev) => ({ ...prev, businessType: undefined }))
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!validateForm()) {
      return
    }

    setSubmitting(true)

    try {
      // 後端 purchaseOrderApprovalThreshold 為 null = 不變更此設定，故留空時不送出此欄位
      const payload: Record<string, unknown> = {
        storeName: formData.storeName,
        businessType: formData.businessType,
        contactEmail: formData.contactEmail,
        contactPhone: formData.contactPhone,
      }
      if (formData.purchaseOrderApprovalThreshold.trim()) {
        payload.purchaseOrderApprovalThreshold = Number(formData.purchaseOrderApprovalThreshold)
      }
      await apiClient.put(API_ENDPOINTS.tenants.update(tenantId), payload)
      // 更新成功後導向至店鋪詳情頁
      router.push(`/dashboard/tenants/${tenantId}`)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        const axiosErr = err as { response?: { status?: number; data?: { message?: string } } }
        if (axiosErr.response?.status === 403) {
          setError('您沒有權限編輯此店鋪，即將跳轉至上一頁...')
          setTimeout(() => {
            router.push('/dashboard/tenants')
          }, 2000)
        } else {
          setError(axiosErr.response?.data?.message || '更新失敗，請稍後再試')
        }
      } else {
        setError('更新失敗，請稍後再試')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-gray-500">載入中...</div>
      </div>
    )
  }

  if (error && !tenant) {
    return (
      <div className="space-y-4">
        <div className="bg-error/10 border border-error/20 text-error px-4 py-3 rounded-md">
          {error}
        </div>
        <Link href="/dashboard/tenants">
          <Button variant="outline">返回店鋪列表</Button>
        </Link>
      </div>
    )
  }

  if (!tenant) {
    return (
      <div className="space-y-4">
        <div className="text-gray-500">找不到店鋪資料</div>
        <Link href="/dashboard/tenants">
          <Button variant="outline">返回店鋪列表</Button>
        </Link>
      </div>
    )
  }

  return (
    <Card className="w-full max-w-lg mx-auto">
      <CardHeader>
        <CardTitle>編輯店鋪</CardTitle>
        <CardDescription>
          修改店鋪的基本資訊
        </CardDescription>
      </CardHeader>
      <form onSubmit={handleSubmit}>
        <CardContent className="space-y-4">
          {error && (
            <div className="bg-error/10 border border-error/20 text-error px-4 py-3 rounded-md text-sm">
              {error}
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor="storeName">
              店鋪名稱 <span className="text-error">*</span>
            </Label>
            <Input
              id="storeName"
              name="storeName"
              type="text"
              placeholder="請輸入店鋪名稱"
              value={formData.storeName}
              onChange={handleInputChange}
              className={errors.storeName ? 'border-error' : ''}
              disabled={submitting}
            />
            {errors.storeName && (
              <p className="text-error text-xs">{errors.storeName}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="businessType">
              營業類型 <span className="text-error">*</span>
            </Label>
            <Select
              value={formData.businessType}
              onValueChange={handleBusinessTypeChange}
              disabled={submitting}
            >
              <SelectTrigger className={errors.businessType ? 'border-error' : ''}>
                <SelectValue placeholder="請選擇營業類型" />
              </SelectTrigger>
              <SelectContent>
                {businessTypes.map((type) => (
                  <SelectItem key={type.value} value={type.value}>
                    {type.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {errors.businessType && (
              <p className="text-error text-xs">{errors.businessType}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="contactEmail">
              聯絡信箱 <span className="text-error">*</span>
            </Label>
            <Input
              id="contactEmail"
              name="contactEmail"
              type="email"
              placeholder="example@company.com"
              value={formData.contactEmail}
              onChange={handleInputChange}
              className={errors.contactEmail ? 'border-error' : ''}
              disabled={submitting}
            />
            {errors.contactEmail && (
              <p className="text-error text-xs">{errors.contactEmail}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="contactPhone">
              聯絡電話 <span className="text-error">*</span>
            </Label>
            <Input
              id="contactPhone"
              name="contactPhone"
              type="tel"
              placeholder="+886-912345678"
              value={formData.contactPhone}
              onChange={handleInputChange}
              className={errors.contactPhone ? 'border-error' : ''}
              disabled={submitting}
            />
            {errors.contactPhone && (
              <p className="text-error text-xs">{errors.contactPhone}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="purchaseOrderApprovalThreshold">
              採購單金額上限（超過需 SuperAdmin 審批，留空表示不設定）
            </Label>
            <Input
              id="purchaseOrderApprovalThreshold"
              name="purchaseOrderApprovalThreshold"
              type="number"
              min="0"
              step="0.01"
              placeholder="不限制"
              value={formData.purchaseOrderApprovalThreshold}
              onChange={handleInputChange}
              className={errors.purchaseOrderApprovalThreshold ? 'border-error' : ''}
              disabled={submitting}
            />
            {errors.purchaseOrderApprovalThreshold && (
              <p className="text-error text-xs">{errors.purchaseOrderApprovalThreshold}</p>
            )}
          </div>

          <div className="pt-4 border-t">
            <p className="text-xs text-muted-foreground">
              店鋪 ID: <span className="font-mono">{tenant.id}</span>
            </p>
            <p className="text-xs text-muted-foreground mt-1">
              店鋪狀態: {tenant.status}
            </p>
          </div>
        </CardContent>
        <CardFooter className="flex justify-between">
          <Link href={`/dashboard/tenants/${tenantId}`}>
            <Button type="button" variant="outline" disabled={submitting}>
              取消
            </Button>
          </Link>
          <Button type="submit" disabled={submitting}>
            {submitting ? '儲存中...' : '儲存更改'}
          </Button>
        </CardFooter>
      </form>
    </Card>
  )
}
