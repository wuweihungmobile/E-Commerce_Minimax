# Sprint 91 Plan — M18 客服工單子系統（後端全部 + 買家前端）

**Sprint**: Sprint 91
**日期**: 2026-07-09
**主題**: PRD §6.10 M18「客服系統」子功能（`docs/01_requirements/M18_Knowledge_Management_SPEC.md` 第 5 章），連續 8 個 Sprint（83-90）列為候選、Sprint 90 retro 明確排定本輪處理的 Phase 2-B 功能。截至目前為完全 greenfield（後端零程式碼、前端零資產）。

---

## 1. 探查結果（現況，Explore agent 完整確認）

- **無編號衝突**：`/v2/media`、`/v2/knowledge`、`/v2/faqs` 與「客服工單」同屬 M18 模組的不同子功能群組（媒體中心 Phase 1、知識庫+FAQ Phase 2-A 皆已交付，客服工單 Phase 2-B 從未實作）。
- **權威規格**：`docs/01_requirements/M18_Knowledge_Management_SPEC.md` 第 5 章（line 248-315）完整定義資料模型、API、RBAC 矩陣。
- **零既有資產**：後端/前端皆無任何 Ticket/Support 相關程式碼。Migration 最新為 V67，本次從 **V68** 起。ErrorCode `E_8xxx` 最新為 E_8007，本次從 **E_8008** 起。
- **易混淆的歷史陷阱**：`SPRINT_13_TASKS.md` 的「M18 Phase 2」任務是知識庫版本控制/排程發布，**不是**客服工單，兩者同名不同功能，探查已確認排除誤判。
- **可參考的既有基礎設施**：Chat 模組（M10）的 Conversation+Message+STOMP 模式可參考訊息串設計，但 PRD 已明確設計獨立的 `support_messages` 表（含 `sender_type` CUSTOMER/STAFF/SYSTEM，Chat 的 Message 沒有這個區分），不重用 Chat 的表結構。Notification 模組（M09）的 `NotificationType` enum 目前無工單相關類型，需新增。

## 2. 架構決策

1. **資料模型比照 PRD spec 原樣落地**（`support_tickets` + `support_messages`，欄位如 spec 第 5.3 節），`tenant_id` **nullable**——買家提交工單時若帶 `orderId`，自動從該訂單反查其 `tenantId` 填入（比照 PRD §7「與 M05 訂單關聯」整合點，且訂單擁有權需驗證：僅能用自己的訂單建立關聯工單）；若無 `orderId`（如帳號/技術問題），`tenant_id` 為 `null`，視為平台工單，僅 ADMIN/SUPER_ADMIN 可見/可處理，不會出現在任何店家的工單列表。
2. **權限模型新增 4 個 Permission**（`support_ticket:read`/`create`/`update`/`manage:all`），角色指派比照 PRD RBAC 矩陣：`BUYER`（read+create，範圍限自己）、`STORE_OWNER`/`STORE_STAFF`（read+create+update，範圍限自己租戶且 `tenant_id` 非 null）、`ADMIN`（read+create+update+manage:all，跨租戶）、`SUPER_ADMIN` 自動全權限。`SELLER`/`HOST`/`GUEST`/`CFO` 不涉及工單，不指派。
3. **一個 Controller 涵蓋三層路由**（比照 `SettlementController` 同檔涵蓋商家+admin 兩層路由的既有慣例）：`SupportTicketController`（`@RequestMapping("/v2")`），路由分別為 `/support/tickets`（買家）、`/dashboard/support/tickets`（店家）、`/admin/support/tickets`（平台）。三層權限相同（`support_ticket:read`/`create`/`update`），實際範圍隔離**在 Service 層用 `TenantContext`/`customerId` 過濾**，而非用不同權限碼區分（因為 STORE_OWNER 與 ADMIN 對「更新工單」這個動作語意相同，只是可視範圍不同，比照既有 `isSuperAdmin` 布林旗標模式，但此處改用「呼叫的是哪個路由」決定 Service 方法要用哪種過濾邏輯，因三個路由各自呼叫不同 Service 方法，天然隔離）。
4. **兩個 Service**：`SupportTicketService`（工單生命週期：建立/列表/詳情/狀態更新/指派）、`SupportMessageService`（工單訊息串：發送/列表）。比照既有 `SettlementGenerator`/`SettlementReviewer`/`SettlementReversalService` 依職責拆分服務類別的慣例。
5. **DTO 集中在一個檔案** `SupportTicketDto.java`（含多個 static 巢狀類別：`TicketDto`/`MessageDto`/`CreateTicketRequest`/`CreateMessageRequest`/`UpdateTicketStatusRequest`/`AssignTicketRequest`/`TicketListResponse`），比照既有 `AdminDto.java` 的多角色 DTO 集中慣例（客服工單同樣是多角色共用的資源）。
6. **不做自動通知整合（實作前重新探查後修正的決策）**：規劃時原假設可比照既有事件模式串接通知，但實作前逐一確認發現 `ORDER_CONFIRMED`/`PAYMENT_SUCCESS`/`BOOKING_CONFIRMED` 等既有 `NotificationType` 在 order/booking/payment 任何業務服務中都**沒有**被實際呼叫觸發（`NotificationProducerService.sendToQueue` 目前僅被 `NotificationService` 自己呼叫，即由使用者/測試顯式呼叫發送 API，並非業務事件自動觸發）——代表「業務事件自動觸發通知」在此程式碼庫**沒有任何既有前例**可循，並非本 Sprint 能直接複製的既有慣例，而是要新發明一套整合架構的獨立設計決策，超出「PRD §5.2 訊息記錄」明確要求的範圍（PRD 該節只要求對話記錄，未列出「自動通知」為必要功能列項）。故本 Sprint **不新增通知整合**，僅實作 PRD 明確列出的訊息記錄（`support_messages`）本身；`NotificationType` enum 暫不變更。
7. **工單編號生成**：`TK-{yyyyMMdd}-{當日序號3位}`（比照 PRD 範例 `TK-20260409-001`），比照既有 `SettlementGenerator.generateStatementNumber` 的日期+序號慣例。
8. **前端範圍**：本 Sprint 僅完成**買家端**（提交工單、我的工單列表、工單詳情含訊息串）。店家/平台前端因涉及獨立的 Dashboard/Admin UI 範圍，改列 **Sprint 92**（比照 Sprint 89/90 大功能拆分慣例），後端三層 API 本 Sprint 一次到位，不因前端分期而分期。

## 3. 資料庫變更（Migration V68）

```sql
CREATE TABLE support_tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NULL REFERENCES tenants(id),
    ticket_number VARCHAR(20) NOT NULL UNIQUE,
    category VARCHAR(20) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    customer_id UUID NOT NULL REFERENCES users(id),
    assigned_to UUID NULL REFERENCES users(id),
    order_id UUID NULL REFERENCES orders(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    resolved_at TIMESTAMP NULL
);
CREATE INDEX idx_support_tickets_tenant_id ON support_tickets(tenant_id);
CREATE INDEX idx_support_tickets_customer_id ON support_tickets(customer_id);

CREATE TABLE support_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES support_tickets(id),
    sender_id UUID NOT NULL REFERENCES users(id),
    sender_type VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    attachments JSONB NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_support_messages_ticket_id ON support_messages(ticket_id);
```

## 4. 後端實作清單（依 CLAUDE.md 開發-編譯-測試循環，一次一個檔案）

1. Migration `V68__Create_Support_Tickets_Tables.sql`
2. `ErrorCode.java`：新增 `E_8008`（找不到工單）～`E_8011`（無效狀態轉換/無權操作他人工單等，依實作中實際需求數量調整）
3. `Permission.java`：新增 4 個權限；`RolePermissionMapping.java`：依上方角色矩陣指派
4. `domain/model/support/SupportTicket.java`、`SupportMessage.java`（entity + enum：`TicketCategory`/`TicketStatus`/`TicketPriority`/`SenderType`）
5. `domain/repository/support/SupportTicketRepository.java`、`SupportMessageRepository.java`
6. `api/dto/SupportTicketDto.java`（集中巢狀 DTO）
7. `core/support/SupportTicketService.java`（create/list（買家自己/店家租戶/平台跨租戶三種查詢方法）/get/updateStatus/assign）
8. `core/support/SupportMessageService.java`（postMessage/listMessages，含工單擁有權檢查）
9. ~~`Notification.java` enum 新增三個類型~~ — 已取消，見上方架構決策 6
10. `api/controller/support/SupportTicketController.java`（三層路由，9 個端點）
11. 各層單元測試（`SupportTicketServiceTest`/`SupportMessageServiceTest`，比照既有 mock 慣例；重點案例：買家只能看自己工單、店家只能看自己租戶工單且 `tenant_id` 為 null 的平台工單不可見、非擁有者操作應拒絕、狀態機轉換驗證）

## 5. 前端實作清單（買家端，Sprint 91 範圍）

1. `lib/api.ts`：新增 `support.tickets`（create/list/detail/messages）區塊
2. 新建 `services/support.ts`
3. 新頁面 `app/support/tickets/page.tsx`（我的工單列表 + 提交新工單表單，比照 `app/addresses/page.tsx` 的 inline 表單慣例）
4. 新頁面 `app/support/tickets/[id]/page.tsx`（工單詳情 + 訊息串 + 發送訊息輸入框，訊息串 UI 可參考 `components/chat/MessageThread.tsx` 但不共用元件本身，因資料結構不同）

## 6. 測試計畫

- 後端：新模組首次落地，屬修改生產邏輯的 Sprint，依 [[sprint-full-regression-policy]] 完成後跑一次 `mvn verify -Pintegration-test` 全量整合回歸（里程碑檢查點，新模組進入資料庫層需驗證 migration 與既有 schema 無衝突）。
- 前端：比照既有慣例，僅 `npm run lint` + `npm run build`。

## 7. 範圍外（延後）

- Sprint 92：店家 Dashboard 工單管理頁、平台 Admin 工單列表+指派頁（前端）。
- DEF-043/DEF-044、`RELEASE_TRACKER.md` 補列、`reversal-candidates` Controller 層測試補強 → 待排入。
