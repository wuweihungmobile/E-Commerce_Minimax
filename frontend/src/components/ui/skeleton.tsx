'use client'

import { cn } from '@/lib/utils'

type SkeletonProps = React.HTMLAttributes<HTMLDivElement>

// 設計稿骨架屏底色 --color-skeleton（#F3F4F6，不隨主題變）。
function Skeleton({ className, ...props }: SkeletonProps) {
  return (
    <div
      className={cn('animate-pulse rounded-md bg-rs-skeleton', className)}
      {...props}
    />
  )
}

export { Skeleton }
