# Sprint 92 Plan — M18 客服工單子系統（店家 + 平台前端）

**Sprint**: Sprint 92
**日期**: 2026-07-09
**主題**: Sprint 91 明確排定範圍外的後半——店家 Dashboard 工單管理頁、平台 Admin 工單列表/指派頁前端。後端三層 API 已於 Sprint 91 全部備妥，本 Sprint 補一個實作前發現的小缺口後即可純前端串接。

---

## 1. 探查結果（現況，延續 Sprint 91 收尾時的發現）

- PRD API 規格表（`M18_Knowledge_Management_SPEC.md` 第 5.4 節）**沒有列出**「店家/平台取得單一工單詳情」端點，僅有 `GET /dashboard/support/tickets`（列表）與 `GET /admin/support/tickets`（列表）。但列表回應（`TicketListResponse`）不含 `messages`，若不補上單筆詳情端點，店家/平台前端將無法顯示對話記錄、也無法在回覆前看到完整脈絡——這與 Sprint 91 為買家端保留 `GET .../tickets/:id` 內嵌 messages 的理由完全一致，是規格表的疏漏而非刻意省略。
- **好消息：不需要新增 Service 方法**。`SupportTicketService.getTenantTicket(ticketId, tenantId, isSuperAdmin)`（Sprint 91 已寫好但未接線）已完整涵蓋所需邏輯：非 SUPER_ADMIN 限自己租戶、SUPER_ADMIN 略過租戶篩選（傳 `tenantId=null, isSuperAdmin=true` 即可直接服務平台層查詢，不需另寫 admin 專用方法）。本 Sprint 只需在 Controller 新增兩個路由呼叫既有方法。

## 2. 架構決策

1. **新增 2 個 GET 端點**：`GET /v2/dashboard/support/tickets/{id}`（`support_ticket:read`，比照既有 dashboard 端點的 `isSuperAdmin` 分支）、`GET /v2/admin/support/tickets/{id}`（`support_ticket:manage:all`，內部呼叫 `getTenantTicket(id, null, true)` 略過租戶篩選）。
2. **前端沿用 Sprint 91 的 `services/support.ts`**，擴充店家/平台方法，不新建服務檔案（同一資源、同一組 DTO，避免重複定義）。
3. **店家/平台前端各採「列表頁 + 詳情頁」兩頁**（與買家端結構一致，因為兩者都需要「瀏覽列表 → 進入單筆看對話串 → 回覆/更新狀態」的完整互動，比 Sprint 89 M16 的單頁卡片模式更適合此處的多輪對話場景）。
4. **店家詳情頁**：狀態下拉選單（`OPEN/IN_PROGRESS/RESOLVED/CLOSED`，依既有狀態機只顯示合法的下一個狀態選項）+ 回覆輸入框。
5. **平台詳情頁**：額外顯示指派表單（`assignedTo` UUID 輸入，作業上通常搭配使用者查詢功能，但目前無使用者搜尋 API，本 Sprint 僅提供直接輸入 UUID 的極簡表單，未來若有使用者選擇器需求再擴充）。

## 3. 後端實作清單

1. `SupportTicketController.java`：新增 `getTenantTicketDetail`（`GET /dashboard/support/tickets/{id}`）、`getAdminTicketDetail`（`GET /admin/support/tickets/{id}`）。
2. 無需新增/修改 Service 方法（重用 Sprint 91 既有 `getTenantTicket`）。
3. 無需新增測試（`getTenantTicket` 的兩種分支已在 Sprint 91 的 `SupportTicketServiceTest` 覆蓋；新增的只是 Controller 路由接線，比照既有其他端點未逐一補 Controller 層測試的慣例，記錄為與 Sprint 90/91 相同的技術債，不在本 Sprint 額外補）。

## 4. 前端實作清單

1. `lib/api.ts`：`dashboard.support.tickets`（list/detail/updateStatus/messages）、`admin.support.tickets`（list/detail/assign）。
2. `services/support.ts`：擴充 `listTenantTickets`/`getTenantTicket`/`updateTicketStatus`/`postStaffMessage`（店家）、`listAllTickets`/`getAdminTicket`/`assignTicket`（平台）。
3. 新頁面 `app/dashboard/support/tickets/page.tsx`（列表）+ `app/dashboard/support/tickets/[id]/page.tsx`（詳情+狀態更新+回覆）。
4. 新頁面 `app/admin/support/tickets/page.tsx`（列表，跨租戶）+ `app/admin/support/tickets/[id]/page.tsx`（詳情+指派+回覆）。

## 5. 測試計畫

- 後端：僅新增 Controller 路由（重用既有已測試的 Service 方法），依 [[sprint-full-regression-policy]] 跑 `mvn test -DexcludedGroups=slow` 即可。
- 前端：`npm run lint` + `npm run build`。

## 6. 範圍外（延後）

- Controller/E2E 層 403 測試補強（累積技術債，含 Sprint 90 `reversal-candidates`、Sprint 91 三層路由）。
- 使用者選擇器（指派工單時用姓名/email 搜尋而非直接輸入 UUID）。
- DEF-043/DEF-044、`RELEASE_TRACKER.md` 補列。
