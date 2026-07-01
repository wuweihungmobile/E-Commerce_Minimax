'use client'

import { useState } from 'react'
import ReviewStars from './ReviewStars'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Alert, AlertDescription } from '@/components/ui/alert'

export interface ReviewFormValue {
  rating: number
  title?: string
  content: string
  isAnonymous: boolean
}

interface ReviewFormProps {
  onSubmit: (value: ReviewFormValue) => Promise<void>
  onCancel?: () => void
}

export default function ReviewForm({ onSubmit, onCancel }: ReviewFormProps) {
  const [rating, setRating] = useState(5)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [isAnonymous, setIsAnonymous] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async () => {
    if (!content.trim()) {
      setError('請填寫評價內容')
      return
    }
    if (rating < 1 || rating > 5) {
      setError('請選擇 1–5 星')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await onSubmit({
        rating,
        title: title.trim() || undefined,
        content: content.trim(),
        isAnonymous,
      })
    } catch (err: unknown) {
      const errorResponse = err as { response?: { data?: { code?: string; message?: string } } }
      const code = errorResponse?.response?.data?.code
      if (code === 'E_1093' || code === 'E_1094') {
        setError('您已評價過此項目')
      } else {
        setError(errorResponse?.response?.data?.message ?? '送出評價失敗，請稍後再試')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="space-y-4">
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="space-y-2">
        <Label>評分 *</Label>
        <ReviewStars rating={rating} onChange={setRating} size="lg" />
      </div>

      <div className="space-y-2">
        <Label htmlFor="reviewTitle">標題（選填）</Label>
        <Input
          id="reviewTitle"
          placeholder="一句話總結你的體驗"
          maxLength={100}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="reviewContent">內容 *</Label>
        <textarea
          id="reviewContent"
          className="flex min-h-24 w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          placeholder="分享你的使用心得（最多 2000 字）"
          maxLength={2000}
          value={content}
          onChange={(e) => setContent(e.target.value)}
        />
      </div>

      <label className="flex items-center gap-2 text-sm text-gray-700">
        <input
          type="checkbox"
          className="h-4 w-4"
          checked={isAnonymous}
          onChange={(e) => setIsAnonymous(e.target.checked)}
        />
        匿名評價
      </label>

      <div className="flex gap-3">
        <Button onClick={handleSubmit} disabled={submitting}>
          {submitting ? '送出中…' : '送出評價'}
        </Button>
        {onCancel && (
          <Button variant="outline" onClick={onCancel} disabled={submitting}>
            取消
          </Button>
        )}
      </div>
    </div>
  )
}
