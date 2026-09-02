# Sprint 108 Plan — 追 DEF-058 的前提，追出 M17 開店審核的真正斷鏈（DEF-059 修復 / DEF-060 記錄）

**Sprint**: Sprint 108
**日期**: 2026-09-02
**AI 編號**: AI-2442
**主題**: 本輪原訂處理 DEF-058（`at-m17-002` E2E 前提造不出來）。查證前提來源時，先推翻了 S107 對 DEF-058 的根因記錄，再往上追出兩件更重要的事：Admin Dashboard 的待審核數**恆為 0**（DEF-059，已修），以及 Admin 開店審核台**整個前端接在 Sprint 97 已知失效的流程上**（DEF-060，記錄待產品拍板）。

---

## 1. 起點：S107 記錄的根因是錯的

S107 把 DEF-058 記為「前提測試自己建立不出來，資料只能來自 `at-m17-001` 副作用或環境殘留」。這條線索**經查證不成立**。

真正的來源就在倉庫裡：

```sql
-- backend/src/main/resources/db/migration/V7__M17_Test_Data_Init.sql:31
-- 2. PENDING_REVIEW 狀態的測試租戶 (for AT-M17-002 審核測試)
INSERT INTO tenants (id, name, slug, status, ...) VALUES ('bbbb...', 'pending-review-tenant', 'pending-test', 'PENDING_REVIEW', ...)
```

前提不是造不出來，是**被測試自己吃掉**：Flyway 版本化遷移只跑一次，播下**固定一筆**；`at-m17-002` 核准它之後該筆變成 `ACTIVE`，沒有任何機制把它復原。所以行為是「乾淨 DB 首跑 pass → 之後永遠 skip」——這才是 skip／pass／fail 三態擺盪的成因，與並行副作用無關。

**教訓**：S107 的結論是在「用 `POST /v2/tenants/apply` 手動 seeding 失敗」之後下的，把「我試的那條路造不出來」推廣成了「沒有任何路造得出來」。這與 [[e-commerce-prd-gap-sprints-93-94]] 記過的「橫向掃描會找對位置但推錯後果」是同一種錯：**證據支持的範圍比結論小**。追蹤表已更正，否則下一輪會照著錯線索再查一次。

---

## 2. 往上追一層：`PENDING_REVIEW` 在生產環境根本不可達

既然固件是硬塞進 DB 的，該問的是：**正常流程會不會產生 `PENDING_REVIEW` 租戶？**

逐一查證全部 9 個 `tenantRepository.save` 呼叫點：

| 位置 | 性質 |
|------|------|
| `AdminService:826`（`approveTenantApplication`） | **唯一的建立點**，寫死 `status=ACTIVE` |
| 其餘 8 處（`TenantService`×1、`TenantStripeConnectService`×3、`AdminService`×4） | 全是既有租戶的更新 |

`Tenant.builder()` 在生產程式碼中只出現一次（同上），`new Tenant(` 零次。結論：**生產環境沒有任何路徑會讓租戶進入 `PENDING_REVIEW`**。該狀態只在 entity/DB 預設值與測試固件中存在。

這點 Sprint 97 其實已經知道，且是刻意的（S97 計畫書 §2.1 逐字記錄：舊端點「本身邏輯正確，只是在正常流程中永遠等不到資料」）。**所以「舊端點是死的」不是本輪的新發現，不重翻。** 但 S97 漏掉了兩個依附在這個死狀態上的東西——那才是本輪的產出。

---

## 3. DEF-059（已修）：Admin Dashboard 的待審核數恆為 0

### 判定依據：自相矛盾，不是不一致

沿用 S107 立下的判準。`AdminService` **同一個類別**裡：

- `getPendingTenantApplications()` → 回傳 N 筆待審核申請
- `getPlatformStats().pendingTenantReviews` → 回報「待審核 **0**」

沒有任何設計會要求後台同時說「有 N 筆等你審」和「待審核 0 筆」。這是自相矛盾，不是風格不一致。

根因：後者計數 `Tenant.status == PENDING_REVIEW`（§2 已證不可達），而真正的待審核佇列在 `tenant_applications.status = PENDING`。

**為什麼長期沒被發現**：`AdminServiceTest.getPlatformStats_returnsStats` 斷言了 5 個欄位，**唯獨沒有斷言 `pendingTenantReviews`**。

### 紅燈

`IT-M17-APP-006`，加在既有 `TenantApplicationReviewE2ETest`：

```
[送出開店申請後，Admin Dashboard 的待審核店鋪數應該 +1]
expected: 1
 but was: 0
```

回應體同時證實了 §2 的推論：`{"totalTenants":3,"activeTenants":3,...,"pendingTenantReviews":0}`——`activeTenants == totalTenants`，DB 裡連一筆 `PENDING_REVIEW` 都沒有。

**刻意採差分斷言（送申請前後應 +1），不用 `>= 1`**：測試 DB 經 Flyway V7 播有 `PENDING_REVIEW` 租戶固件，`>= 1` 會被那筆固件矇混成假綠——正是 [[e-commerce-prd-gap-sprints-93-94]] 記過的「測試以固件繞過同一段邏輯」。

### 修法

```java
int pendingReviews = (int) tenantApplicationRepository
        .countByStatus(TenantApplication.ApplicationStatus.PENDING);
```

新增 `TenantApplicationRepository.countByStatus`。原本的 `tenantRepository.findAll().stream().filter(...)` 一併移除（該行就是缺陷本身，非順手重構）。程式碼中留下註解說明為何不可改回以 `Tenant.status` 計數，比照 S107 對 DEF-037 的做法——**決策要活在程式碼裡，不是只活在追蹤表裡**。

### 防回歸

除整合測試外，另加一個**能辨別方向**的單元測試：mock 令 tenants 側存在 1 筆 `PENDING_REVIEW`、applications 側 3 筆，斷言結果為 **3**。若有人改回數 Tenant，會拿到 1 而失敗。

---

## 4. DEF-060（記錄，未動）：Admin 審核台前端接在失效流程上

修 DEF-059 時順著同一條線發現的，比 DEF-059 嚴重得多。

實際的產品路徑：

```
網友送出開店申請 → POST /v2/tenants/apply → 寫入 tenant_applications (PENDING)
                                                      ↓
Admin 後台 /admin/tenants「審核中」分頁 → GET /v2/admin/tenants?status=PENDING_REVIEW → 永遠空
Admin /admin/tenants/[id]/review 核准鈕 → POST /v2/admin/tenants/{id}/approve → 永遠 E_2005
                                                      ↓
真正能核准的 /v2/admin/tenant-applications/*（S97 新增）→ 全前端零引用
```

已查證：`grep -r "tenant-applications" frontend/`（排除 `node_modules`）**無任何命中**。Sprint 97 補上了後端橋接、也記錄了舊端點是死的，但**沒有改前端**，所以從使用者視角看，缺口其實沒有關閉：網友送出的開店申請，Admin 在 UI 上永遠看不到、也無法核准。M17「開店申請 + 審核」在 PRD 中標為 **P0**。

### 為什麼本輪不修

修法有兩條路，差別是產品決策而非工程細節：

- **(a) 改前端審核台接 `tenant-applications` 端點** — 推薦，順著 S97 既有設計，`tenant_applications` 保持為申請的真實來源。
- **(b) 依 PRD 第 340 行的生命週期圖 `[申請開店] → PENDING_REVIEW → [Admin 審核] → ACTIVE` 改回申請時即建立 `PENDING_REVIEW` 租戶** — 推翻 S97 設計，`tenant_applications` 變冗餘，但既有 admin 前端不必改。

PRD §340 描述的是 (b)，實作走的是 (a)——**規格與實作在此處分歧**。這屬功能開發而非測試強化，且兩條路的工作量與資料模型故事完全不同，未在本輪自行決定。已記錄為 DEF-060（高優先級）。

⚠️ 附帶影響：**DEF-058 被 DEF-060 阻擋**。`at-m17-002` 驅動的正是這個接在死流程上的審核台；若決策走 (a)，該 spec 需整支改寫。先修測試會被丟棄，故等 DEF-060 拍板後一併處理。

---

## 5. 測試變更

| 測試 | 類型 | 說明 |
|------|------|------|
| `IT-M17-APP-006`（新增） | 整合 | 差分斷言：送出開店申請後 Dashboard 待審核數 +1。修復前紅燈 `expected: 1 but was: 0` |
| `getPlatformStats_pendingReviewsCountsApplicationsNotTenants`（新增） | 單元 | 辨別方向：tenants 側 1 筆 `PENDING_REVIEW` vs applications 側 3 筆，斷言 3 |


### 全量回歸（本輪改動生產邏輯，依政策執行）

`cd backend && mvn verify`：**單元 1022 / 整合 415，0 failures 0 errors**，checkstyle **0 violations**（main + test），BUILD SUCCESS。

相對 S107 的 1021 / 414，兩邊各 +1，正是本輪新增的兩個測試——已確認新增案例真的被執行，而非被 surefire／failsafe 的 include/exclude 濾掉（見 [[mvn-test-excludes-integration-test-files]]）。

---

## 6. 範圍外（延後）

- **DEF-060**：Admin 審核台前端改接 —— 待產品拍板（見 §4）。
- **DEF-058**：`at-m17-002` 前提穩定化 —— 被 DEF-060 阻擋（見 §4）。
- **舊 `Tenant` 審核端點**（`reviewTenant`/`approveTenant`/`rejectTenant`）—— Sprint 97 已知情且刻意保留，本輪不重翻、不刪除。若 DEF-060 決策走 (a)，屆時才是處理它們的時機。

---

## 7. 方法論教訓

1. **「我試的那條路造不出來」≠「沒有任何路造得出來」**。S107 的 DEF-058 根因就是這樣推錯的。結論的涵蓋範圍必須等於證據的涵蓋範圍。
2. **記錄錯誤的根因，代價會延續到下一輪**。追蹤表是下一輪的起點，錯的線索會讓人照著再查一次——所以本輪的產出之一是**更正 S107 的記錄**，而不只是往前推進。
3. **「已知並刻意」要能被查到才算數**。S97 把「舊端點永遠等不到資料」寫進了計畫書，本輪因此沒有重翻它——但寫在計畫書裡不夠，依附於該死狀態的 dashboard 計數與前端審核台就沒人檢查。決策要釘在**程式碼**裡（本輪 DEF-059 的註解即為此）。
4. **差分斷言 vs 門檻斷言**：當環境可能已有固件時，`>= 1` 是假綠溫床，「動作前後的增量」才驗證得到因果。
