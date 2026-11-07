// 賣家外殼（賣家後台 dashboard／平台管理台 admin）的導覽設定——側欄的單一來源。
// （原本還有第三個區域 cms〔內容管理〕，Sprint 332 的 DEF-466 把它的貼文／媒體介面併進 dashboard，區域與 /cms 路由一併移除。）
//
// 標籤與 data-testid 沿用原本儀表板首頁、管理台首頁的「快速入口」（E2E 依賴它們：at-m15-e2e 找連結
// 『商品管理』『房型管理』、at-dashboard-tenant-nav-real 點 dashboard-tenants-link、at-admin-overview-real 點
// admin-tenants-link）。側欄裡每個目的地只出現一次；頁面內已有的「動作」（例如貼文管理的『新建貼文』按鈕）不重複放進側欄，
// 免得同一頁出現兩個同名連結（Playwright strict mode 會直接失敗）。

export type SellerArea = "dashboard" | "admin"

export interface SellerNavItem {
  href: string
  label: string
  /** 沿用原快速入口的 data-testid */
  testId?: string
  /** true＝網址必須完全相同才算目前頁（區域首頁、ERP 首頁用；否則所有子頁都會讓它亮起） */
  exact?: boolean
}

export interface SellerNavSection {
  title?: string
  items: SellerNavItem[]
}

export interface SellerAreaConfig {
  /** 頁首顯示的區域名稱 */
  title: string
  /** 側欄標題（也是 <nav> 的 aria-label） */
  navTitle: string
  sections: SellerNavSection[]
}

export const SELLER_AREAS: Record<SellerArea, SellerAreaConfig> = {
  dashboard: {
    title: "賣家中心",
    navTitle: "賣家後台",
    sections: [
      { items: [{ href: "/dashboard", label: "營運總覽", exact: true }] },
      {
        title: "商品與房源",
        items: [
          { href: "/dashboard/products", label: "商品管理" },
          { href: "/dashboard/rooms", label: "房型管理" },
          { href: "/dashboard/pricing/rules", label: "定價規則", testId: "dashboard-pricing-link" },
          // 標籤沿用該頁標題與原本定價規則頁頂欄裡那個連結的文字『定價日曆預覽』（at-dashboard-pricing-calendar-real 依賴）
          { href: "/dashboard/pricing/calendar", label: "定價日曆預覽" },
        ],
      },
      {
        title: "訂單與營收",
        items: [
          { href: "/dashboard/orders", label: "訂單管理", testId: "dashboard-orders-link" },
          { href: "/dashboard/bookings", label: "訂房管理", testId: "dashboard-bookings-link" },
          { href: "/dashboard/revenue", label: "營收報表" },
          { href: "/dashboard/returns", label: "退貨審核", testId: "dashboard-returns-link" },
          { href: "/dashboard/shipping", label: "運費模板設定", testId: "dashboard-shipping-link" },
        ],
      },
      {
        title: "顧客服務",
        items: [
          { href: "/dashboard/reviews", label: "評價管理", testId: "dashboard-reviews-link" },
          { href: "/dashboard/support/tickets", label: "客服工單" },
          { href: "/dashboard/chat", label: "訊息中心" },
          { href: "/dashboard/notifications", label: "通知模板" },
        ],
      },
      {
        title: "進銷存 ERP",
        items: [
          { href: "/dashboard/erp", label: "ERP 總覽", exact: true },
          { href: "/dashboard/erp/suppliers", label: "供應商" },
          { href: "/dashboard/erp/purchase-orders", label: "採購訂單" },
          { href: "/dashboard/erp/inventory", label: "庫存" },
          { href: "/dashboard/erp/stock-movements", label: "庫存異動" },
        ],
      },
      {
        title: "內容行銷",
        items: [
          { href: "/dashboard/posts", label: "貼文管理" },
          { href: "/dashboard/posts/categories", label: "貼文分類" },
          { href: "/dashboard/faq", label: "FAQ 管理" },
          { href: "/dashboard/faq/categories", label: "FAQ 分類" },
          { href: "/dashboard/knowledge", label: "知識庫" },
          { href: "/dashboard/media", label: "媒體中心" },
        ],
      },
      {
        title: "店鋪設定",
        items: [
          { href: "/dashboard/tenants", label: "店鋪管理", testId: "dashboard-tenants-link" },
          { href: "/dashboard/members", label: "店鋪成員管理", testId: "dashboard-members-link" },
        ],
      },
    ],
  },
  admin: {
    title: "平台管理台",
    navTitle: "平台管理",
    sections: [
      { items: [{ href: "/admin", label: "平台總覽", exact: true }] },
      {
        title: "管理功能",
        items: [
          { href: "/admin/tenants", label: "租戶管理", testId: "admin-tenants-link" },
          { href: "/admin/audit-logs", label: "稽核紀錄", testId: "admin-audit-logs-link" },
          { href: "/admin/purchase-orders", label: "採購單審批", testId: "admin-purchase-orders-link" },
          { href: "/admin/settlements/reversal", label: "結算沖正", testId: "admin-settlements-link" },
          { href: "/admin/support/tickets", label: "客服工單", testId: "admin-support-tickets-link" },
        ],
      },
    ],
  },
}

/**
 * 目前網址對應到側欄的哪一項：取「網址完全相同，或網址以 `href/` 開頭」的項目中 href 最長者
 * （例如 /dashboard/posts/categories 只讓『貼文分類』亮起，不會連『貼文管理』一起亮）。
 * 沒有任何項目符合（例如不在側欄裡的詳情頁路徑）回 null。
 */
export function findActiveHref(pathname: string, config: SellerAreaConfig): string | null {
  let best: string | null = null
  for (const section of config.sections) {
    for (const item of section.items) {
      const hit = item.exact
        ? pathname === item.href
        : pathname === item.href || pathname.startsWith(`${item.href}/`)
      if (hit && (best === null || item.href.length > best.length)) best = item.href
    }
  }
  return best
}
