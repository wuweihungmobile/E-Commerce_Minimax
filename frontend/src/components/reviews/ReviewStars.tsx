'use client'

interface ReviewStarsProps {
  rating: number
  // 提供 onChange 即為可互動評分輸入；否則為唯讀顯示
  onChange?: (rating: number) => void
  size?: 'sm' | 'md' | 'lg'
}

const SIZE_CLASS: Record<NonNullable<ReviewStarsProps['size']>, string> = {
  sm: 'text-base',
  md: 'text-xl',
  lg: 'text-3xl',
}

export default function ReviewStars({ rating, onChange, size = 'md' }: ReviewStarsProps) {
  const interactive = typeof onChange === 'function'
  const sizeClass = SIZE_CLASS[size]

  return (
    <div className="inline-flex items-center gap-0.5" role={interactive ? 'radiogroup' : undefined}>
      {[1, 2, 3, 4, 5].map((star) => {
        const filled = star <= rating
        const content = (
          <span className={filled ? 'text-amber-400' : 'text-gray-300'}>★</span>
        )
        if (!interactive) {
          return (
            <span key={star} className={sizeClass} aria-hidden="true">
              {content}
            </span>
          )
        }
        return (
          <button
            key={star}
            type="button"
            className={sizeClass + ' leading-none transition-transform hover:scale-110'}
            onClick={() => onChange?.(star)}
            aria-label={`${star} 星`}
          >
            {content}
          </button>
        )
      })}
    </div>
  )
}
