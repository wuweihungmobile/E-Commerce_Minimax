import * as React from "react"

import { statusEntry, type StatusDomain, type StatusEntry } from "@/lib/status"
import { Badge } from "./badge"

// 狀態徽章：標籤與顏色由 lib/status.ts 的登錄表決定，頁面只說「這是哪個領域的什麼狀態」。
//   <StatusBadge domain="post" status={post.status} />
//   <StatusBadge entry={featureStatusEntry(f.status, f.requiresAdminReview, f.isEnabled)} />
type StatusBadgeProps = { className?: string } & (
  | { domain: StatusDomain; status: string; entry?: never }
  | { entry: StatusEntry; domain?: never; status?: never }
)

function StatusBadge({ className, ...props }: StatusBadgeProps) {
  const resolved = props.entry ?? statusEntry(props.domain, props.status)
  return (
    <Badge variant={resolved.variant} className={className}>
      {resolved.label}
    </Badge>
  )
}

export { StatusBadge }
