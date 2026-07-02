# 買家閉環 Live 走查 Checklist / Buyer Journey Live Walkthrough Checklist

> **建立日期**: 2026-07-02
> **Sprint**: Sprint 41 US-005（AI-1903，順延多 Sprint 後之交付）
> **對應成果**: Sprint 37~41 買家體驗（共用版型 + 詳情頁 + ROOM 訂房閉環 + 整月日曆）
> **用途**: 供人工在**真實運行的全棧環境**逐項走查買家旅程，記錄缺陷
> **維護者**: QA Quincy + Claude Code

---

## 🔴 為什麼需要這份 Checklist（誠實界線）

**AI 無法代替人類做真人部署環境走查。** AI-1903 已順延多個 Sprint，原因正是它需要「真實 live 環境 + 真人點擊驗證」。本 Sprint 的交付分為兩部分：

1. **自動化證據（AI 可做，已執行）**：`make validate-e2e` 啟動**真後端 JAR（:8080）+ 真前端（:3000）+ 乾淨 DB（Flyway 重建）+ Playwright**，鏡像雲端 e2e job。涵蓋「前端 + 路由 + client 接線 + 後端啟動/schema 驗證」。
2. **手動走查（需人類，本文件）**：真 DB 落地的 **order → pay → notify → ship → review** 全鏈，需 cross-role seed + 部署環境，只能由人工走查。

> ⚠️ **殘留（Rule 12 誠實揭露）**：自動化 E2E 的買家流程 spec 多以 `page.route` mock 或空狀態驗證，證明前端/路由/接線正確，但**未證明真 DB 落地的跨角色資料流**。下方「B. 需真環境的深度走查」即為此殘留，AI-1903 續留待真人執行。

---

## A. 環境啟動（走查前置）

### A-1. 啟動全棧
- [ ] Docker Desktop 運行中（`docker info` 正常）
- [ ] 啟動全棧：`make up`（frontend :3000 / backend :8080 / postgres :5432 / redis :6379）
- [ ] 等待 backend 健康：`curl -s http://localhost:8080/api/actuator/health` 回 `{"status":"UP"}`

### A-2. 🔴 關鍵環境變數修正（DEF-014 雙前綴陷阱）
- [ ] **確認 `NEXT_PUBLIC_API_URL=http://localhost:8080/api`**（**不是** `/api/v2`）
  - 原因：前端每個端點已自帶 `/v2`（見 `frontend/src/lib/api.ts`）；若設 `/api/v2` 會變成 `/api/v2/v2/...` → 401
  - compose 預設值為 `/api/v2`（`docker-compose.yml`），走查前需覆寫為 `/api`
  - 對照：`scripts/validate-e2e.sh` 即用 `http://localhost:8080/api` 並附警告註解

### A-3. 🔴 種子資料（否則首頁無商品可逛）
- [ ] 確認首頁有 ACTIVE listings 可瀏覽
  - dev override 以 `SPRING_FLYWAY_ENABLED=false` + `ddl-auto=update` 啟動 → **Flyway 種子（V5/V7）不會套用** → 首頁可能空白
  - 走查前擇一：(a) 用 prod profile 全棧（Flyway on）；(b) 手動 insert 一筆 ACTIVE ROOM + 一筆 ACTIVE PRODUCT；(c) 透過賣家後台建立
- [ ] 至少有 1 筆 ROOM listing（供整月日曆 / 訂房走查）+ 1 筆 PRODUCT listing（供加購走查）

---

## B. 買家閉環走查（逐項打勾）

### B-1. 首頁 / 賣場版型（Sprint 35/37）
- [ ] 開 `http://localhost:3000/` → 首頁載入，共用 StorefrontHeader + Footer 顯示
- [ ] 未登入時 Header 顯示「登入 / 註冊」
- [ ] 商品格線（`product-grid`）顯示 listings；排序 tab 可切換
- [ ] 搜尋列可輸入並送出（不與其他表單衝突）
- [ ] 主題切換正常，重新整理後保留（localStorage）

### B-2. 註冊 / 登入（Sprint 37 共用版型）
- [ ] `/register` 於共用版型內，可完成註冊 → 導回 `/login`
- [ ] `/login` 可登入 → 導向 dashboard/首頁
- [ ] 登入後 Header 帳號選單顯示 email + 「我的訂單 / 登出」
- [ ] 「登出」後 Header 回到訪客態（登入 / 註冊）

### B-3. 商品詳情（Sprint 38）
- [ ] 點首頁商品卡 → `/listings/{id}` 詳情頁渲染（`listing-detail`）
- [ ] PRODUCT：可調數量 → 「加入購物車」成功（`listing-add-success`）
- [ ] 未登入直接開詳情 → 顯示「請先登入」引導（`listing-auth-empty`）
- [ ] 不存在的 id → 「找不到商品」（`listing-notfound`）

### B-4. ROOM 整月日曆 + 即時可用性（Sprint 40/41）⭐ 本 Sprint 新增
- [ ] ROOM 詳情頁顯示**整月日曆**（`listing-calendar`）
- [ ] 日曆可切換上/下個月（`calendar-prev` / `calendar-next`）
- [ ] **已被預訂的日期**顯示灰色刪除線且**不可點選**（`data-unavailable="true"`、disabled）
- [ ] 過去日期不可選
- [ ] 點選可訂日 → 帶入入住日；再點後續可訂日 → 帶入退房日（與日期輸入框同步）
- [ ] 點「查詢可用性」→ 可訂：顯示「可預訂 · N 晚合計 NT$X」（`listing-availability`）
- [ ] 選到已訂區間 → 顯示「此日期不可預訂（原因）」（`listing-unavailable`）+ 加購鈕禁用
- [ ] 可訂 → 「加入購物車」帶日期成功

### B-5. 購物車 → 結帳 → 訂房（Sprint 39）
- [ ] `/cart` 顯示已加入項目；未登入 → 導向 `/login`
- [ ] 促銷碼套用 / 移除、數量調整、移除項目正常
- [ ] 「前往結帳」→ `/checkout`
- [ ] 填訪客資料（姓名/電話/email）→「確認預訂」
- [ ] 成功 → 「預訂成功！」+ 預訂編號；購物車清空
- [ ] 日期衝突（併發訂走）→「所選日期已被預訂」優雅提示（非通用錯誤）

### B-6. 訂單 / 預訂 / 通知（Sprint 32/37）
- [ ] `/orders` 訂單列表（空狀態或有資料）
- [ ] `/bookings` 我的預訂列表；`/bookings/{id}` 詳情
- [ ] `/notifications` 收件匣 + 篩選切換

---

## C. 需真環境 + cross-role seed 的深度走查（AI-1903 殘留）

> 以下需**跨角色資料**（買家下單 + 賣家出貨 + 系統通知/物流）落地，`page.route` mock 無法涵蓋，僅能人工於部署環境走查。

- [ ] 完整金流：下單 → 付款（對 Mock 金流）→ 訂單狀態機 CREATED→PAID→...
- [ ] 物流：賣家出貨 → 買家 `/orders/{id}` 物流追蹤（logistics tracking）顯示真資料
- [ ] 通知：付款/出貨事件 → 買家 `/notifications` 即時收到對應通知
- [ ] 評價：完成訂單 → `/reviews/product/{listingId}` 提交評價 → 詳情頁評分更新
- [ ] 訂單詳情頁（order detail）完整資料流（目前僅空狀態 E2E 覆蓋）

---

## D. 走查結果記錄

| 項目 | 結果（✅/❌/N/A） | 缺陷描述 | 追蹤 ID |
|------|------------------|----------|---------|
| （逐項填寫） | | | |

**走查人**: ＿＿＿＿　**日期**: ＿＿＿＿　**環境**: local `make up` / staging / prod

---

## E. 自動化證據對照（本 Sprint 已執行）

- `make validate-e2e`：真後端 JAR + 真前端 + 乾淨 DB + Playwright（複製雲端 e2e job）
- 買家閉環相關 spec：
  - `at-homepage.spec.ts`（首頁版型 / 主題 / 搜尋 / 401 引導）
  - `at-listing-detail.spec.ts`（格線 → 詳情 → PRODUCT 加購 / 401 / 404）
  - `at-room-booking.spec.ts`（ROOM 可用性 / 整月日曆 E2E-ROOM-05 / checkout 建立 booking / 409 衝突）
  - `at-buyer-pages.spec.ts`（orders/bookings/notifications 空狀態 + Header 帳號選單登出）
- Playwright HTML 報告：`frontend/playwright-report/`

> **證據強度誠實揭露**：上述 spec 證明前端 + 路由 + client 接線正確、後端可啟動且 schema 對齊；但買家 HTTP 呼叫多為 `page.route` mock，**未證明真 DB 落地的跨角色資料流**（見 C 節殘留）。

---

**文件版本**: v1.0
**建立者**: QA Quincy + Claude Code
**基於**: AISDLC v0.09
