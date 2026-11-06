// 狀態登錄表：「中文標籤＋語意色」的單一來源（配合 components/ui/status-badge.tsx 的 <StatusBadge>）。
//
// 為什麼集中：Sprint 329 之前，同一個概念由 16 個檔案各自決定顏色，而且互相矛盾——
// 貼文管理的「草稿」是灰、「已歸檔」是黃；知識庫的「草稿」是黃、「已封存」是灰。顏色是業務語意，不是各頁的裝飾。
//
// 語意色規則（跨領域一致；Badge 的 variant）：
//   outline     草稿（中性、尚未完成）
//   warning     待審／審批中／部分到貨（需要有人處理）
//   success     啟用／已發布／已核准／已到貨／上架中
//   secondary   停用／已封存／已歸檔／已下架（中性、不再生效）
//   destructive 駁回／取消／停權／終止／拒絕
//   default     已提交（流程中、等待下一步）
// 標籤文字沿用各頁原本的中文（E2E 與使用者都看得到）；要新增狀態或領域：只在這裡加一列。
//
// 不在這裡：訂單、訂房、退貨、店鋪成員、付款、物流的狀態——它們早已集中在各自的 services/*.ts
// （`ORDER_STATUS_LABELS`＋`orderStatusBadgeVariant` 等），維持原樣。

export type StatusVariant = 'default' | 'secondary' | 'destructive' | 'success' | 'outline' | 'warning'

export interface StatusEntry {
  label: string
  variant: StatusVariant
}

const e = (label: string, variant: StatusVariant): StatusEntry => ({ label, variant })

const STATUS = {
  // CMS 貼文
  post: {
    DRAFT: e('草稿', 'outline'),
    PUBLISHED: e('已發布', 'success'),
    ARCHIVED: e('已歸檔', 'secondary'),
  },
  // 知識庫文章（同貼文，但封存的字面是「已封存」）
  knowledgeArticle: {
    DRAFT: e('草稿', 'outline'),
    PUBLISHED: e('已發布', 'success'),
    ARCHIVED: e('已封存', 'secondary'),
  },
  // 店鋪（對齊後端 Tenant.TenantStatus 實際值域，Sprint 146）
  tenant: {
    PENDING_REVIEW: e('審核中', 'warning'),
    ACTIVE: e('已核准', 'success'),
    REJECTED: e('已拒絕', 'destructive'),
    SUSPENDED: e('已停權', 'destructive'),
    TERMINATED: e('已終止', 'destructive'),
  },
  // 採購訂單
  purchaseOrder: {
    DRAFT: e('草稿', 'outline'),
    SUBMITTED: e('已提交', 'default'),
    PENDING_APPROVAL: e('待審批', 'warning'),
    APPROVED: e('已核准', 'success'),
    REJECTED: e('已駁回', 'destructive'),
    PARTIALLY_RECEIVED: e('部分到貨', 'warning'),
    RECEIVED: e('已到貨', 'success'),
    CANCELLED: e('已取消', 'destructive'),
  },
  // 供應商
  supplier: {
    ACTIVE: e('啟用', 'success'),
    INACTIVE: e('停用', 'secondary'),
  },
  // 商品、房源（listing）
  listing: {
    ACTIVE: e('上架中', 'success'),
    INACTIVE: e('已下架', 'secondary'),
    DRAFT: e('草稿', 'outline'),
  },
  // 定價規則（後端欄位是 isActive 布林；呼叫端轉成 ACTIVE／INACTIVE）
  pricingRule: {
    ACTIVE: e('啟用中', 'success'),
    INACTIVE: e('已停用', 'secondary'),
  },
  // 通用的「啟用／停用」旗標（通知模板、貼文分類…；後端欄位是 isActive 布林，呼叫端轉成 ACTIVE／INACTIVE）
  activation: {
    ACTIVE: e('啟用', 'success'),
    INACTIVE: e('停用', 'secondary'),
  },
} as const satisfies Record<string, Record<string, StatusEntry>>

/** 整張登錄表（唯讀）：供「跨領域語意一致」的契約測試遍歷（frontend/e2e/at-ui-design-system.spec.ts）。 */
export const STATUS_REGISTRY: Readonly<Record<string, Readonly<Record<string, StatusEntry>>>> = STATUS

export type StatusDomain = keyof typeof STATUS

/** 查登錄表；沒登錄的狀態值原樣顯示並用 outline（不吞掉未知狀態）。 */
export function statusEntry(domain: StatusDomain, status: string): StatusEntry {
  const table: Record<string, StatusEntry> = STATUS[domain]
  return table[status] ?? e(status, 'outline')
}

/**
 * 功能開關（feature toggle）的狀態：店主端與管理台同一套邏輯。
 * 待審核必須先判斷：PENDING 時 isEnabled 本身就是 false（DEF-247 既有設計），
 * 若先判斷 !enabled 會讓「待審核」被誤標成「已停用」，看起來已直接被駁回（DEF-359）。
 */
export function featureStatusEntry(status: string, requiresAdminReview: boolean, enabled: boolean): StatusEntry {
  if (requiresAdminReview && status === 'PENDING') return e('待審核', 'warning')
  if (!enabled) return e('已停用', 'secondary')
  if (status === 'ACTIVE') return e('已啟用', 'success')
  return e(status, 'secondary')
}
