'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import SupportService, { type SupportTicket, type CreateTicketInput, type TicketCategory } from '@/services/support'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { StorefrontShell } from '@/components/layout/StorefrontShell'

const EMPTY_FORM: CreateTicketInput = {
  category: 'OTHER',
  subject: '',
  description: '',
}

const CATEGORY_OPTIONS: Array<{ value: TicketCategory; label: string }> = [
  { value: 'PRODUCT', label: '商品問題' },
  { value: 'BOOKING', label: '預訂問題' },
  { value: 'PAYMENT', label: '付款問題' },
  { value: 'TECHNICAL', label: '技術問題' },
  { value: 'OTHER', label: '其他' },
]

const STATUS_LABEL: Record<SupportTicket['status'], string> = {
  OPEN: '開立',
  IN_PROGRESS: '處理中',
  RESOLVED: '已解決',
  CLOSED: '已關閉',
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosErr = error as { response?: { data?: { message?: string } } }
    return axiosErr.response?.data?.message || fallback
  }
  return fallback
}

export default function SupportTicketsPage() {
  const [tickets, setTickets] = useState<SupportTicket[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState<CreateTicketInput>(EMPTY_FORM)
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError(null)
      try {
        const result = await SupportService.listMyTickets()
        if (cancelled) return
        setTickets(result.tickets)
      } catch {
        if (cancelled) return
        setError('無法載入工單列表，請稍後再試')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => {
      cancelled = true
    }
  }, [])

  async function reload() {
    const result = await SupportService.listMyTickets()
    setTickets(result.tickets)
  }

  function startCreate() {
    setForm(EMPTY_FORM)
    setFormError(null)
    setShowForm(true)
  }

  function cancelForm() {
    setShowForm(false)
    setForm(EMPTY_FORM)
    setFormError(null)
  }

  function validateForm(): string | null {
    if (!form.subject.trim()) return '問題主旨為必填'
    if (!form.description.trim()) return '問題描述為必填'
    return null
  }

  async function handleSubmit() {
    const validationError = validateForm()
    if (validationError) {
      setFormError(validationError)
      return
    }
    setSubmitting(true)
    setFormError(null)
    try {
      await SupportService.createTicket({
        category: form.category,
        subject: form.subject.trim(),
        description: form.description.trim(),
      })
      cancelForm()
      await reload()
    } catch (err) {
      setFormError(extractErrorMessage(err, '提交工單失敗'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <StorefrontShell>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">我的客服工單</h1>
          <p className="mt-1 text-sm text-gray-600">
            {loading ? '載入中…' : `共 ${tickets.length} 筆工單`}
          </p>
        </div>
        <Button onClick={startCreate}>提交新工單</Button>
      </div>

      {error && (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {showForm && (
        <Card className="mb-6">
          <CardContent className="py-5">
            <h2 className="text-sm font-semibold mb-3">提交新工單</h2>
            {formError && (
              <Alert variant="destructive" className="mb-3">
                <AlertDescription>{formError}</AlertDescription>
              </Alert>
            )}
            <div className="space-y-3">
              <div>
                <Label className="mb-1 block">問題類型 *</Label>
                <select
                  className="w-full border rounded-md px-3 py-2 text-sm"
                  value={form.category}
                  onChange={(e) => setForm({ ...form, category: e.target.value as TicketCategory })}
                >
                  {CATEGORY_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                  ))}
                </select>
              </div>
              <div>
                <Label className="mb-1 block">問題主旨 *</Label>
                <Input
                  value={form.subject}
                  onChange={(e) => setForm({ ...form, subject: e.target.value })}
                  maxLength={200}
                />
              </div>
              <div>
                <Label className="mb-1 block">問題描述 *</Label>
                <textarea
                  className="w-full border rounded-md px-3 py-2 text-sm min-h-24"
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                />
              </div>
            </div>
            <div className="flex gap-2 mt-4">
              <Button onClick={handleSubmit} disabled={submitting}>
                {submitting ? '提交中...' : '提交'}
              </Button>
              <Button variant="outline" onClick={cancelForm}>
                取消
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {loading ? (
        <div className="space-y-4">
          {[0, 1].map((i) => (
            <Card key={i}>
              <CardContent className="py-6">
                <Skeleton className="h-5 w-40 mb-3" />
                <Skeleton className="h-4 w-24" />
              </CardContent>
            </Card>
          ))}
        </div>
      ) : tickets.length === 0 && !error ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-16">
            <div className="text-5xl mb-4">🎫</div>
            <h2 className="text-lg font-semibold text-gray-900 mb-1">尚無客服工單</h2>
            <p className="text-sm text-gray-500 mb-6">有問題想詢問？提交工單讓客服協助您</p>
            <Button onClick={startCreate}>提交新工單</Button>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-4">
          {tickets.map((ticket) => (
            <Link key={ticket.id} href={`/support/tickets/${ticket.id}`}>
              <Card className="hover:border-rs-primary transition-colors">
                <CardContent className="py-5">
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-sm font-medium text-gray-900">{ticket.subject}</span>
                        <Badge variant={ticket.status === 'CLOSED' ? 'outline' : 'default'}>
                          {STATUS_LABEL[ticket.status]}
                        </Badge>
                      </div>
                      <p className="text-sm text-gray-500">
                        {ticket.ticketNumber} · {new Date(ticket.createdAt).toLocaleDateString('zh-TW')}
                      </p>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </StorefrontShell>
  )
}
