# Sprint 110 Plan — PRD §340 文件同步：M17 開店/審核流程規格對齊實作（ER-004）

**Sprint**: Sprint 110
**日期**: 2026-09-02
**AI 編號**: AI-2444
**主題**: Sprint 109 把 M17 開店流程的三段斷鏈修通，但**規格文件仍描述著被推翻的那套流程**。本輪只做文件同步，**零程式碼變更**。

---

## 1. 使用者決策

Sprint 109 收尾時呈上三個方向，使用者選 **PRD §340 文件同步**，理由是「放著會讓下一輪掃描重複查一次」。

Sprint 108 已拍板 **(a) 前端改接 `tenant-applications`**，即**以實作為準**。本輪據此把文件改成實作的樣子，
而不是反過來把實作改成 PRD 的樣子。

---

## 2. 要同步的那一個事實

```
PRD v1.0 §4.3（第 340 行）說：
    [申請開店] → PENDING_REVIEW → [Admin 審核] → ACTIVE
    （申請時就建立 Tenant，審核只是改它的狀態）

實作做的是：
    申請 → 只寫 tenant_applications（status=PENDING），此時「沒有 Tenant 這個東西」
    核准 → 才建立 Tenant，且寫死 status=ACTIVE
    駁回 → 只改申請狀態，完全不建立 Tenant
```

**推論**：`Tenant.PENDING_REVIEW` 在生產環境不可達。這不是本輪的新發現，是 Sprint 108 逐一查證
9 個 `tenantRepository.save` 呼叫點的既有結論；本輪只是把它寫回規格。

### 2.1 查證依據（本輪重新確認，非引用舊結論）

| 事實 | 出處 |
|------|------|
| 申請只寫 `tenant_applications`，`status=PENDING` | `TenantService.createApplication()`（`core/tenant/TenantService.java:84-118`） |
| 重複申請只擋 `PENDING`（駁回後可重新申請），錯誤碼 `E-4092` | 同上 L86-91；`ErrorCode.E_4092` |
| `Tenant` 唯一建立點，寫死 `ACTIVE` | `AdminService.approveTenantApplication()`（`core/admin/AdminService.java:823-831`） |
| 核准同一交易內：Feature Toggle → `tenant_members`(STORE_OWNER) → `users.role` → 回寫申請 | 同上 L833-854（`users.role` 同步為 Sprint 98 `6453cd3` 補上） |
| 駁回不建立 `Tenant`，只寫 `rejection_reason` | `AdminService.rejectTenantApplication()`（L871-896） |
| 核准／駁回錯誤碼 `E-2006`／`E-2007`／`E-2008` | `ErrorCode.java:25-27` |
| 審核端點與角色 | `AdminController` L116/126/140，三者皆 `@PreAuthorize("hasRole('SUPER_ADMIN')")` |
| `tenants.status` 無 CHECK 約束、DB 預設 `PENDING_REVIEW` | `V1__Initial_Schema.sql:21` |
| `tenant_applications` 欄位與索引 | `V4__Tenant_Application.sql:9-30` |
| 前端已改接新端點 | `frontend/src/app/admin/tenants/page.tsx:77`（Sprint 109） |

---

## 3. 變更清單（8 份文件，全部為文件）

### 3.1 PRD `docs/01_requirements/E-Commerce_PRD_v1.0_Final.md`（v1.0 → v1.0.1）

| 章節 | 變更 |
|------|------|
| §4.3 租戶生命週期（原第 340 行） | 由單一實體狀態圖改寫為**兩表兩階段**；拆成 `tenant_applications.status` 與 `tenants.status` 兩張狀態表；標註 `PENDING_REVIEW`／`REJECTED` 為生產不可達的歷史保留值，並釘死「待審核佇列查 `tenant_applications.status='PENDING'`」 |
| §6.8 Feature Toggle 初始化時序 | 觸發點由「`tenants.status` PENDING_REVIEW→ACTIVE」改為「核准申請而建立 Tenant 時」 |
| §7.4.1 角色授予流程 | 由 2 項資料表變更補齊為實作的 5 項（含 Feature Toggle 初始化、`users.role` 同步、申請回寫） |
| §8.2.1 tenants | `status` 欄位標註生產可達值；補上「申請資料不在本表」的說明 |
| §8.2.1-A tenant_applications | **新增**（PRD 原本完全沒有這張表） |
| §9.10.2 租戶管理 API | 審核入口由 `PUT /admin/tenants/:id/review` 更正為 `GET/POST /admin/tenant-applications/*`；角色 `Admin` → `SUPER_ADMIN`；Feature Toggle 端點補 `:feature`；加註舊端點保留但不可用 |
| §22 勘誤記錄 | 新增 **ER-004** |
| 標頭 + 文件修訂紀錄 | v1.0.1 |

### 3.2 FRD `docs/01_requirements/E-Commerce_FRD_v1.0.md`（v1.0 → v1.2）

§8.3 資料模型（`tenants.status` 修正 + **新增 `tenant_applications`**）、AC-M17-001-1、US-M17-001 邊界條件（錯誤碼 `E-4002`→`E-4092`）、
US-M17-007 全部 AC 與邊界條件、US-M17-008 全部 AC 與邊界條件、BR-M17-001（生命週期圖）、BR-M17-002（初始化觸發點）、§8.6 API 概要。

> **FRD v1.1 的歷史教訓值得記一筆**：2026-04-22 的 v1.1 把「租戶狀態 PENDING_REVIEW → PENDING」列為修正項。
> 它改對了名稱、卻**改錯了實體**——申請狀態根本不在 `tenants` 上。改名讓文件看起來被維護過，反而更難發現真正的分歧。

### 3.3 API 規格

- `docs/02_architecture/API_Index.md`：新增 `API-M17-APP-001~003`；`API-M17-008/009` 標記為舊流程（**保留編號不刪**，因 Sprint 03／03-A 的歷史測試案例仍引用）；`API-M17-002` 路徑更正為 `/tenants/my`。
- `docs/02_architecture/api/API_M17_Tenant.md`：§8／§9 由舊端點改寫為三個真實端點的完整規格（含 Request/Response 實際欄位與錯誤情境表）。

### 3.4 SRD（加註，不改寫）

- `SRD_Module_Technical_Design.md` §2 M17：加上「設計稿 vs 實作現況」對照表，明確警告**照本節程式碼草稿實作會做出錯誤的流程**，原文保留為設計決策的歷史紀錄。
- `SRD_Database_Schema.md` §2.1.1 `tenants`：加註「沒有申請中狀態」與「請以 Flyway 遷移為準」。

---

## 4. 範圍外（已觀察、刻意未動）

| 項目 | 為什麼不動 |
|------|-----------|
| **舊 `Tenant` 審核端點的存廢**（`/admin/tenants/review`、`/:id/approve`、`/:id/reject`） | 沿用 S109 的判斷：移除 PRD 標為 P0 的端點是產品決策，非工程決策。本輪只在文件標註「不可用」 |
| **`SRD_Database_Schema.md` §2.1.1 的其餘 schema 漂移** | 該 DDL 與 `V1__Initial_Schema.sql` 有 8 個欄位級落差（`store_name`／`owner_id`／`business_type`／`approved_at`／`approved_by`／`rejected_at`／`rejected_by`／兩個 UNIQUE 約束皆不存在）。這是**另一個題目**（設計稿 vs 實作的全表對照），不塞進本輪。已記為 **DEF-062** |
| **`Tenant.TenantStatus` enum 中的死值** | 移除 `PENDING_REVIEW`／`REJECTED` 需同步處理 DB 預設值、Flyway V7 固件、舊端點與其測試——是程式碼變更，不是文件同步 |

---

## 5. 驗證

**本輪零程式碼變更**，故不跑全量回歸（依既有政策：純文件變更不觸及生產邏輯）。實際驗證方式：

1. **反向掃描**：改完後重跑 `grep -rn "PENDING_REVIEW\|PENDING'" docs/01_requirements/ docs/02_architecture/`，
   逐條確認殘留命中**全部**是「刻意保留的說明文字」或「非 M17 的結算狀態機」，無一是仍在陳述舊流程的斷言。
2. **端點交叉比對**：文件中每一個 M17 端點路徑，逐一對照 `AdminController`／`TenantController` 的
   `@RequestMapping` 實際值（見 §2.1 表）。過程中額外抓到三處與本題無關但同屬「文件端點不存在」的錯誤，
   已一併更正（`API-M17-002` 的 `/tenants` → `/tenants/my`；FRD §8.6 的 `/dashboard/tenant/profile` 根本不存在）。
3. **守門**：`.git/hooks/pre-push` 第 81-96 行對「無 `backend/` 與 `frontend/` 變動」的推送**明文放行**
   （「純文件/設定，輕量守門對其無意義」）。本輪符合該條件，不跑 `make validate-release`——
   這是專案守門自己寫下的豁免，不是繞過。

---

## 5.1 途中被自己的守門擋下：pre-commit 機密掃描的檔案級誤判（使用者拍板）

文件全部寫完、要 commit 時被 pre-commit 的 secret 掃描擋下，指向 `E-Commerce_FRD_v1.0.md`。

**查證結果：觸發的 5 行全部是既有內容，本輪一行都沒動**——都在 M03 認證章節的示例 payload：

| 行號 | 內容形狀 | 所在段落 |
|------|----------|----------|
| 1420、1481 | `password` 欄位帶引號示例值 | 登入請求／回應範例 |
| 1487、1555 | `accessToken` 欄位帶引號的 JWT 片段 | 登入／換發 token 回應範例 |
| 1502 | 內文行寫著 `password` 加引號值 | 錯誤密碼的測試輸入說明 |

（此處刻意不照抄原文——照抄會讓本計畫書自己變成新的命中行，見下方紅燈驗證 D 組。）

**根因**：掃描用 `grep -l` 做**檔案級**比對——只要檔案裡任何一處命中就整檔擋下，
連本次一個字都沒改的內容也算。該 FRD 自初始 commit 後從未被修改，所以這些行從來沒被掃到；
本輪為了別的理由動到它，就被它自己的既有內容擋下。既有排除清單已涵蓋 `test/`、`e2e/`、`.spec.ts`
——正是「示例憑證合理存在」的地方——但沒有涵蓋規格文件。

**這是安全守門的變更，未自行決定，呈上三案由使用者拍板**，使用者選 **(1) 改為只掃新增行**：

| 案 | 取捨 |
|----|------|
| **(1) 只掃 staged diff 的 `+` 行**（採用） | 偵測強度不降反升（連命中的那一行都印出來）；既有內容不再因為碰了同一個檔案被翻出來 |
| (2) 排除清單加 `^docs/` | 一行改動，但整個 `docs/` 從此不受機密掃描保護——真的把金鑰貼進文件也不會被攔 |
| (3) 改 FRD 那 5 行示例值 | 不動守門，但改到與本輪無關的 M03 章節，且讓規格示例失真 |

**紅燈驗證（三組對照，缺一不可）**：

| 對照組 | 期望 | 結果 |
|--------|------|------|
| A：本輪 staged 內容（含既有 FRD 示例） | 放行 | ✅ `EXIT_CODE=0` |
| B：新檔案新增一行 `api_key` 賦值（值為 `sk-live-` 開頭的假 token） | 攔截 | ✅ `EXIT_CODE=1`，並印出該行 |
| C：**在同一個 FRD 上新增**一行 `password` 賦值 | 攔截 | ✅ `EXIT_CODE=1`，並印出該行 |
| D：本計畫書原稿照抄 FRD 那 5 行原文 | 攔截 | ✅ 實際發生——新守衛在本輪 commit 時攔下**我自己新增的引用行**，改為上表的描述式寫法 |

**C 組是關鍵**：只有 A + B 會誤以為「把該檔案豁免掉」也能過關。C 證明修法沒有整檔放行——
同一個檔案裡，既有示例放行、新增機密照樣攔。

**D 組是意外收穫**：本計畫書原稿把 FRD 那 5 行原文照抄進來說明問題，結果**被新守衛當場攔下**。
那些行對這份文件而言確實是「新增的、含機密樣式的行」，攔得完全正確——
守衛不是在紙上被驗證的，是在本輪自己的 commit 上被驗證的。

變更檔案：`scripts/hooks/pre-commit`（以 `make hooks-install` 安裝，`.git/hooks/` 已同步），
並在程式碼註解釘死「不要改回檔案級比對」的理由——**決策要活在程式碼裡**（沿用 S107 DEF-037 的做法）。

---

## 6. 方法論教訓

1. **「文件被維護過」不等於「文件是對的」。** FRD v1.1 修過這個欄位、改對了名稱卻改錯了實體，
   結果是分歧被一次無效的維護蓋掉，多存活了 4 個月。**看到修訂紀錄裡有相關條目時，要更用力查，不是更放心。**
2. **設計稿型文件要標註而非改寫。** SRD 的類別草稿與序列圖是設計期的決策紀錄，把它改成實作現況等於
   抹掉「當初想的和後來做的不一樣」這個資訊。加對照表能同時保住歷史與可用性。
3. **規格與實作分歧時，要先確認方向由誰決定。** 本輪能直接改文件，是因為 S108 使用者已拍板以實作為準；
   若無此前提，正確做法是呈上兩條路而不是自行選一條改。
