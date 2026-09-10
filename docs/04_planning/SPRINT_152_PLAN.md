# Sprint 152 Plan — DEF-191：SELLER 訂單履約權限缺口拍板與修復

**Sprint**: Sprint 152
**日期**: 2026-09-10

---

## 1. 起點

延續 [SPRINT_151_PLAN.md](SPRINT_151_PLAN.md) §6「下一步 / Action Items」#7：`DEF-191`——`SELLER` 角色能把訂單確認到 `CONFIRMED`（持有 `order:update`），卻無法建立物流單完成出貨（`LogisticsController.createLogistics` 要求 `order:create`，`SELLER` 沒有）。Sprint 151 已查明此為既有權限矩陣疏漏（`RolePermissionMapping.java` 其餘角色限制皆有明確理由註解，唯獨此處沒有），非本輪程式碼迴歸，登記待業務決策後排程。

開工時 Sprint 151 的 6 個 push 前置項目（#1~#6）皆已完成並驗證通過（commit `6ab60e8`/`62b24f1`，雲端 CI run 34435711766 三個 job 全綠），故本輪範圍單純聚焦 `DEF-191`，不重新掃描其他模組。

---

## 2. 使用者決策

經 `AskUserQuestion` 徵詢兩輪：

1. **Sprint 152 方向**：在「先拍板 DEF-191」／「新一輪自選掃描」／「處理其他待排程項目」／「使用者另外指定」四個選項中，使用者選擇「先拍板 DEF-191」。
2. **DEF-191 修法**：在「`createLogistics` 改用 `order:update`」／「`SELLER` 角色新增 `order:create` 權限」／「先不修，維持待排程」三個選項中，使用者選擇「`createLogistics` 改用 `order:update`」——理由：語意上更貼近「賣家管理既有訂單的履約進度」而非「建立新訂單」，且不需擴大 `SELLER` 的權限清單本身。

---

## 3. 實作內容

### 3.1 `LogisticsController.createLogistics`：`@PreAuthorize` 由 `order:create` 改為 `order:update`

```java
@PostMapping
@PreAuthorize("hasAuthority('order:update')")
public ResponseEntity<ApiResponse<LogisticsDto.LogisticsResponse>> createLogistics(...)
```

補充 Javadoc 說明 DEF-191 的根因與決策理由。`RolePermissionMapping` **未變動**——`SELLER` 本就持有 `ORDER_UPDATE`，`STORE_OWNER`/`ADMIN` 本就同時持有 `ORDER_CREATE`/`ORDER_UPDATE`，行為不受影響；只有 `SELLER`（原本被擋下）與理論上任何「只有 `order:update` 沒有 `order:create`」的角色會受影響——目前角色矩陣中僅 `SELLER` 符合此條件。

### 3.2 未變動範圍

- `RolePermissionMapping`：不新增 `ORDER_CREATE` 給 `SELLER`（依使用者決策，避免擴大權限清單本身，見 §2）。
- `LogisticsController` 其餘 6 個端點（`getLogistics`/`getLogisticsByOrderId`/`trackLogistics`/`getTrackingDetail`/`updateLogisticsStatus`/`cancelLogistics`）維持既有 `order:read`/`order:update` 需求，不受影響。
- `OrderController`/`OrderService` 零變動——`DEF-191` 純屬 `LogisticsController` 單一端點的授權需求調整。
- 前端零變動：`dashboard/orders/[id]/page.tsx`（Sprint 151 新增）呼叫 `createLogistics` 時本就沒有額外的角色層級限制，後端授權放寬後前端不需任何修改即可讓 `SELLER` 使用「建立物流單」功能。

---

## 4. 測試（紅燈先行且實際執行）

### 4.1 新增測試

- `M11LogisticsOrderIntegrationTest#createLogistics_onlyLegacyOrderCreateAuthority_returns403`（新增）：SELLER 僅持有舊授權 `order:create`（不含 `order:update`）呼叫 `createLogistics` → 斷言 `403`，直接鎖住「舊授權不再放行」這個修法核心行為。
- `RolePermissionMappingTest#seller_hasOrderUpdateButNotOrderCreate`（新增）：鎖住 `SELLER` 持有 `ORDER_UPDATE` 但不持有 `ORDER_CREATE` 這個刻意的權限矩陣邊界，避免未來有人「順手」把 `ORDER_CREATE` 加回 `SELLER` 而破壞本次決策依據。
- `RolePermissionMappingTest#storeOwner_hasBothOrderCreateAndOrderUpdate`（新增）：鎖住 `STORE_OWNER` 不受本次修法影響（本就同時持有兩個權限）。

### 4.2 修改既有測試

`M11LogisticsOrderIntegrationTest` 既有 3 個 IT-SHIP 案例（`createLogistics_confirmedOrder_returns200AndUpdatesOrderToShipping`／`createLogistics_notConfirmedOrder_returns4xx`／`createLogistics_activeLogisticsExists_returns409`）原用 `TestSecurityContextHelper.setUserContext(SELLER_USER_ID, TENANT_ID, "SELLER", "order:create")` 建立測試身份，改為 `"order:update"`——這 3 個既有案例本身就是「SELLER 呼叫 createLogistics 成功路徑」的既有覆蓋，修法後若不同步更新測試授權，會直接紅燈（詳見 §4.3）。

### 4.3 紅燈先行且實際執行（`git stash` 證明修復前後差異）

1. `git stash push -- backend/.../LogisticsController.java`（僅暫存生產程式碼，保留新增/修改的測試）。
2. 對還原後的舊程式碼（`@PreAuthorize("hasAuthority('order:create')")`）跑新增的 `createLogistics_onlyLegacyOrderCreateAuthority_returns403`：
   - 首次執行因未 mock `orderRepository.findById` 等資料，得到 `404`（授權層放行、業務層找不到訂單），訊號不夠明確，補上與 IT-SHIP-001 相同的完整 mock 後重跑。
   - 補 mock 後重跑：**`Status expected:<403> but was:<200>`**——證實修法前這組（僅 `order:create`）授權確實足以通過並成功建立物流單，缺陷真實存在。
3. `git stash pop` 還原修復後的 `LogisticsController.java`，重新編譯測試。
4. `mvn -o test -Dtest=M11LogisticsOrderIntegrationTest,RolePermissionMappingTest` → **20 passed / 0 failed**（5 個 M11LogisticsOrderIntegrationTest + 15 個 RolePermissionMappingTest）。

---

## 5. 驗證結果

### 5.1 後端

- `mvn -o compile` / `mvn -o test-compile`：皆成功。
- `mvn -o test -Dtest=M11LogisticsOrderIntegrationTest,RolePermissionMappingTest`：**20 passed / 0 failed**（含紅燈先行驗證，見 §4.3）。
- `mvn -o verify`（含 Flyway `ddl-auto=validate` 真實 Postgres + Redis 的完整單元＋整合回歸）：第一輪 **1239 單元 + 478 整合，0 failed**，但 `checkstyle:check (checkstyle-main)` 以 `Unable to read Checkstyle results xml: ... in epilog non whitespace content is not allowed but got \u0` 失敗——查證 `target/checkstyle-result.xml` 實際內容是合法的空報告（`<checkstyle version="9.3"></checkstyle>`，代表 0 違規）後面接了大量 `\0` padding 到舊檔案的原始大小，屬前一次執行殘留的損毀暫存檔案，與本輪程式碼無關（比照 CLAUDE.md「先看 actual error 而非猜測」的規則：`python3 -c "import xml.etree.ElementTree"` 直接證實檔案非法 XML，`od -c` 確認 padding 是 null bytes）。刪除該檔後分別以 `mvn -o checkstyle:check@checkstyle-main`／`@checkstyle-test` 獨立驗證皆為 **0 Checkstyle violations**；重新完整跑一次 `mvn -o verify` 取得單一乾淨紀錄：**1239 個單元測試 + 478 個整合測試，0 failed；checkstyle-main/checkstyle-test 皆 0 違規；BUILD SUCCESS**。

### 5.2 前端

本輪零前端程式碼變動（`LogisticsController.java` 的授權需求調整對前端呼叫端透明，`dashboard/orders/[id]/page.tsx` 本就沒有角色層級的額外限制），未執行 `tsc`/`eslint`/`npm run build`。

### 5.3 手動功能驗證（真實 Docker 環境）

`make up` 啟動真實 Docker Compose 全棧（postgres+redis+backend+frontend，等待 `ecommerce-backend-dev` healthcheck 轉 `healthy`）。比照 Sprint 151 §5.3 手法，以 `psql` 直接建立測試資料（繞開 `/tenant/apply` 拿不到 ACTIVE 租戶的問題，見 [[m17-tenant-application-dual-flow]]）：一個 `ACTIVE` 測試租戶、一個歸屬該租戶的 `SELLER` 使用者、一筆 `CONFIRMED` 狀態的 PRODUCT 訂單。透過真實登入取得帶 `role=SELLER`/`tenantId` 的 JWT：

- `POST /v2/logistics`（`{"orderId": "...", "logisticsProvider": "HCT"}`）：**200**（修法前會是 403）,正確回傳 `logisticsId`/`trackingNumber`/`status: PENDING`。
- 事後查詢 `orders` 表：`status` 已原子轉為 `SHIPPING`（`LogisticsService.createLogistics` 既有的 CAS 邏輯正確觸發，不受本輪授權調整影響）。

`make down` 停用 dev stack。未清理測試資料留在 dev docker volume 中屬正常（純本機開發資料庫，比照 Sprint 151 §5.3 慣例）。

`make validate-e2e`（乾淨 DB + host 全棧 + Playwright，複製雲端 e2e job）：**57 passed / 4 skipped / 0 failed**，與 Sprint 145~151 既有基準完全一致；backend 以 `ddl-auto=validate` + Flyway 成功啟動，腳本明確輸出「entity 與 Flyway schema 對齊，無漂移」，確認本輪變更（純授權調整，無 schema 變動）無回歸。

---

## 6. 範圍外（刻意不做，如實揭露）

- **不新增 Playwright E2E**：理由同 Sprint 150 §6／Sprint 151 §4——既有 E2E `registerAndLogin` helper 無法取得 ACTIVE 賣家租戶測試帳號，這是先於本輪存在、範圍更大的既有基礎設施缺口。以 Mockito/MockMvc 整合測試（§4）+ 手動 Docker 驗證（§5.3）補足。
- **不處理 SPRINT_151_PLAN.md §6 其餘待排程項目**（#9 狀態歷史時間軸／#11 配額用量顯示／#12 會員資料匯出前端入口／#13 OAuth／#14 Javadoc 清理）：使用者本輪僅拍板 `DEF-191`，其餘項目維持待排程狀態。

---

## 7. 下一步 / Action Items

依 [SPRINT_ARTIFACT_CONVENTION.md](../05_development/SPRINT_ARTIFACT_CONVENTION.md) v2.0 §3.2，本節為必要章節。

| # | 項目 | 來源 | 狀態 | 去向 |
|---|------|------|------|------|
| 1 | `DEF-191`：`LogisticsController.createLogistics` 改用 `order:update` | Sprint 151 §6 | ✅ 完成 | 詳見 §3.1 |
| 2 | 執行 `mvn -o verify` 完整回歸並回填本節結果 | 本輪交付前 | ✅ 完成 | 見 §5.1，1239 單元 + 478 整合，0 failed，checkstyle 0 違規 |
| 3 | 真實 Docker 環境手動驗證 SELLER 建立物流單 | 本輪交付前 | ✅ 完成 | 見 §5.3，200 + 訂單原子轉 SHIPPING |
| 4 | `make validate-e2e` 回歸確認無 schema/前端漂移 | 本輪交付前 | ✅ 完成 | 見 §5.3，57 passed / 4 skipped，與既有基準一致 |
| 5 | `RELEASE_TRACKER`/`DEFERRED_ITEMS_TRACKER` 回填本輪 push 狀態與雲端 CI 結果 | 本輪交付後 | ⏳ 待執行 | |
| 6 | 賣家訂單詳情頁狀態變更歷史（State Log 時間軸） | Sprint 151 §6 #9 | ⬜ 待排程 | 低優先級，可獨立小型追加 |
| 7 | `DEF-103/104/105` 三筆輸入驗證 | S135 登記 | ⬜ 待排程 | 低優先級，已拍板不排入排程 |
| 8 | `/dashboard/tenants/[id]/features` 顯示頁補上配額用量 | S147 §6 範圍外 | ⬜ 待排程 | 需先確認是否要做 |
| 9 | 會員資料匯出／自助刪除帳戶補前端入口 | S149 §7 範圍外 | ⬜ 待排程 | 需先確認 UI 位置與是否要做 |
| 10 | OAuth 登入/連結串接 | 既有 stub（S78 記錄） | ⬜ 待排程 | 需先確認是否要做 |
| 11 | `AnalyticsService`/`Controller` Javadoc「Mock」字樣過時 | S150 §7 範圍外 | ⬜ 待排程 | 純文件修正，低優先級 |

---

## 8. 誠實揭露總結

- 修法選擇「調整 `createLogistics` 的 `@PreAuthorize`」而非「擴大 `SELLER` 的 `RolePermissionMapping` 權限清單」，是使用者拍板的決策，不是工程單方面判斷（§2）。
- 新增的紅燈測試 `createLogistics_onlyLegacyOrderCreateAuthority_returns403` 第一版因未 mock 訂單資料而得到含糊的 404，不足以證明「授權層放行」；補上與既有 IT-SHIP-001 相同的完整 mock 後才得到明確的 200/403 對比，如實記錄這個過程而非只呈現最終乾淨版本（§4.3）。
- 本輪刻意不處理 Sprint 151 §6 列出的其餘待排程項目——使用者本輪僅拍板 `DEF-191`，範圍界線清楚。
- 第一輪 `mvn -o verify` 回報 `BUILD FAILURE`，依 CLAUDE.md「先看 actual error 而非猜測」規則實際查證後確認與本輪程式碼無關——是前一次執行殘留、內容合法但被 null byte padding 污染的 `checkstyle-result.xml` 暫存檔案（實際內容是 0 違規的空報告），刪除後重新完整跑一次取得乾淨的單一次 `BUILD SUCCESS`，而非略過此顆紅燈或不查證就假設「反正是環境問題」（見 §5.1）。
