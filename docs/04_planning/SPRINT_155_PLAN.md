# Sprint 155 Plan — 查證 Sprint 154 §6 遺留項目：`TenantUpdateRequest.coverImageUrl` 同型協定驗證缺口

**Sprint**: Sprint 155
**日期**: 2026-09-15

---

## 1. 起點

[SPRINT_154_PLAN.md](SPRINT_154_PLAN.md) §4/§6 記錄：查證 DEF-103（`Listing.coverImageUrl`）時，在 `TenantUpdateRequest.coverImageUrl`（店鋪封面圖，不同於 `Listing.coverImageUrl`）發現「可能同型」的協定驗證缺口，但刻意不擴大 DEF-103 範圍修復，留待本輪查證後另案登記。使用者在 Sprint 154 收尾後要求繼續完成此項。

---

## 2. 查證過程與發現

逐行追查 `TenantUpdateRequest.coverImageUrl` 的完整資料流，發現實際情況與原記錄假設的「同型缺口」不同：

| 檢查點 | 結果 |
|--------|------|
| `TenantService.applyTenantUpdates`（第 324-352 行）是否讀取 `request.getCoverImageUrl()` | ❌ 從未讀取（同一份 DTO 只處理了手足欄位 `logoUrl`） |
| `Tenant` 實體是否有對應欄位/資料表 | ❌ 完全沒有 |
| `getTenantDetails`（第 279 行）如何回應 | 硬寫 `.coverImageUrl(null)`，附註解「tenants table doesn't have cover_image_url」 |
| 前端 `TenantEditForm.tsx`/`TenantDetail.tsx` 的 `Tenant` 介面 | ❌ 未宣告此欄位 |

**結論**：`coverImageUrl` 是完全死欄位——店主送出的值會被 API 靜默接受、然後完全丟棄，從未寫入、從未讀出。這**不是**協定驗證缺口（幫一個必然被丟棄的值加 `@Pattern` 沒有意義），而是契約漂移／死欄位類型的技術債（同類見 `DEF-169`/`DEF-025`）。

同一輪查證發現手足欄位 `logoUrl` 才是真正與 `DEF-104`（`CmsDto` Banner `linkUrl`）結構完全同型的缺口：

| 檢查點 | 結果 |
|--------|------|
| 寫入路徑 | ✅ `applyTenantUpdates` 呼叫 `tenant.setLogoUrl(request.getLogoUrl())` |
| 持久化 | ✅ `Tenant.logoUrl` 有對應欄位 |
| 公開讀回端點 | ✅ `GET /v2/tenants/{id}`（`@PreAuthorize("isAuthenticated() or true")`）經 `TenantDetailsResponse.logoUrl` 回傳 |
| 前端消費端 | ❌ `grep` 全庫確認零消費點 |
| 協定驗證 | ❌ 原本只有 `@Size`，無協定黑名單 |

與 `DEF-104` 一致的結構：有寫入/持久化/公開端點回傳，但目前零前端渲染 sink，非可利用漏洞，僅為防禦性缺口。

查證過程中順帶比對同一批 Tenant 相關 DTO 的其他 URL 欄位，另發現 `TenantApplicationRequest.businessLicenseUrl`（開店申請的營業執照網址）同樣有寫入/持久化路徑（`TenantApplication.businessLicenseUrl`）卻無協定驗證，但 `grep` 全庫確認**沒有任何 Response DTO 曾經序列化此欄位**（連公開讀回端點都不存在），比 `logoUrl`/DEF-104 更難構成攻擊鏈。此為本輪查證過程中的額外發現，非原定範圍。

---

## 3. 使用者決策

將 §2 的兩個核心發現（`coverImageUrl` 是死欄位而非驗證缺口；真正同型的缺口在 `logoUrl`）提交 `AskUserQuestion` 徵詢：

- **Q1（`coverImageUrl` 如何處理）**：使用者選擇**「只登記為新 DEF，不動代碼」**——加驗證在會被丟棄的值上沒有意義，屬 speculative 修改（CLAUDE.md Rule 2）。
- **Q2（`logoUrl` 如何處理）**：使用者選擇**「比照 DEF-104 現在就修」**。

`businessLicenseUrl` 的發現不在上述兩個問題範圍內，依 CLAUDE.md Rule 3（精準改動，只碰必須修改的東西）不擅自擴大本輪修復範圍，僅登記備查（`DEF-195`），不修改程式碼。

---

## 4. 實作內容

### 4.1 DEF-194：`TenantUpdateRequest.logoUrl` 協定白名單

[`TenantUpdateRequest.java:41-44`](../../backend/src/main/java/com/nextkey/ecommerce/api/dto/TenantUpdateRequest.java#L41-L44) 新增：

```java
@Size(max = URL_MAX_LENGTH, message = "Logo URL must not exceed 500 characters")
@Pattern(regexp = "^(?!\\s*(?i:javascript|data|vbscript|file):).*$",
        message = "Logo URL must not use javascript/data/vbscript/file protocol")
private String logoUrl;
```

沿用 `DEF-103`/`DEF-104` 已驗證過的同一條負向前瞻（negative lookahead）regex 與訊息風格，保持全代碼庫協定黑名單驗證的一致寫法。`coverImageUrl` 維持既有 `@Size`，刻意不加 `@Pattern`，並在程式碼註解中明確記載死欄位的事實與理由，避免未來被誤以為遺漏。

---

## 5. 範圍外（刻意不做，如實揭露）

- **不修改 `coverImageUrl`**：§2/§3 已說明理由，登記為 `DEF-193`（不排入排程）。
- **不修改 `TenantApplicationRequest.businessLicenseUrl`**：§2 末段的新發現不在使用者本輪核准範圍內，登記為 `DEF-195`（不排入排程），留待未來另案查證。
- **未擴大查證其他 Tenant 相關 URL 欄位**：`TenantDetailsResponse.MemberInfo.avatarUrl`/`TenantMemberResponse.avatarUrl` 雖同屬 Tenant 相關 DTO 的 URL 欄位，但其資料源頭是 `User.avatarUrl`（不同網域物件、不同輸入端點），不在本輪「查證 `TenantUpdateRequest.coverImageUrl`」的既定範圍內，如實記錄未探查、非確認無缺陷。
- **不清除 `coverImageUrl` 死欄位**：使用者已在 Q1 明確選擇「只登記不動代碼」，移除欄位本身（DTO 契約變更）已在候選選項中提出但未被選擇，故維持現狀。

---

## 6. 驗證結果

### 6.1 DEF-194 紅燈先行驗證

新增 `TenantUpdateRequestValidationTest`（11 案例）。先確認綠燈（`Tests run: 11, Failures: 0`）→ 暫時移除 `@Pattern` → 重跑確認 6 個危險協定案例轉紅（`Tests run: 11, Failures: 6`）→ 還原修復 → 重跑確認全數轉綠（`Tests run: 11, Failures: 0`），流程與 `DEF-103`/`104`/`105` 一致。

### 6.2 `mvn -o checkstyle:check@checkstyle-main checkstyle:check@checkstyle-test`

0 違規。

### 6.3 `mvn -o verify`（完整單元＋整合回歸）

**BUILD SUCCESS**：單元測試 **1293 個**（相對 Sprint 154 結束時基準 1282，+11 為本輪新增的 `TenantUpdateRequestValidationTest`），**0 failures / 0 errors**；整合測試 **478 個**（與 Sprint 154 基準持平，本輪未新增整合測試），**0 failures / 0 errors**。

### 6.4 `make validate-e2e`（乾淨 DB + host 全棧 + Playwright）

**62 passed / 4 skipped / 0 failed**，與 Sprint 154 既有基準完全一致（本輪無新增/刪除 E2E 案例，純背景回歸確認），schema 對齊無漂移（`[e2e-gate] ✅ 本地 E2E 守門通過`）。

---

## 7. 下一步 / Action Items

| # | 項目 | 來源 | 狀態 | 去向 |
|---|------|------|------|------|
| 1 | 查證 `TenantUpdateRequest.coverImageUrl` 是否有同型協定驗證缺口 | Sprint 154 §4/§6 遺留 | ✅ 完成 | 查證結論：死欄位，非驗證缺口，見 §2；登記 `DEF-193` |
| 2 | `DEF-194`：`TenantUpdateRequest.logoUrl` 協定白名單 | 本輪查證發現，使用者拍板現在就修 | ✅ 完成 | 詳見 §4.1 |
| 3 | `DEF-195`：`TenantApplicationRequest.businessLicenseUrl` 同型缺口 | 本輪查證過程中的額外發現 | ⬜ 已登記，不排入排程 | 見 §2/§5，留待未來查證 |
| 4 | `mvn -o verify` 完整回歸 | 本輪交付前 | ✅ 完成 | 見 §6.3 |
| 5 | `make validate-e2e` 回歸確認無 schema/前端漂移 | 本輪交付前 | ✅ 完成 | 見 §6.4 |

---

## 8. 誠實揭露總結

- 原記錄假設 `coverImageUrl` 是「同型協定驗證缺口」，查證後推翻此假設——它是完全死欄位，真正同型的缺口在手足欄位 `logoUrl`，已在 §2 明確說明並更正。
- 查證過程中另發現 `TenantApplicationRequest.businessLicenseUrl` 有類似（但更難構成攻擊鏈的）缺口，刻意不在本輪修復，登記為 `DEF-195`，避免範圍蔓延。
- 未查證 `avatarUrl` 等其他 Tenant 相關 DTO 的 URL 欄位，如實記錄為未探查範圍，非確認無缺陷。
- `coverImageUrl` 死欄位本身（API 靜默接受並丟棄使用者送出的值）是否需要清理或恢復功能，是需求層級的產品決策，非本輪技術缺陷修復範圍，已如實記錄於 `DEF-193`。
