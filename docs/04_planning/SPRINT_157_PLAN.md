# Sprint 157 Plan — 結清 DEF-195：`TenantApplicationRequest.businessLicenseUrl` 協定白名單驗證

**Sprint**: Sprint 157
**日期**: 2026-09-15

---

## 1. 起點

[SPRINT_155_PLAN.md](SPRINT_155_PLAN.md) §2/§5 記錄：查證 `DEF-193`/`DEF-194` 時順帶發現 `TenantApplicationRequest.businessLicenseUrl`（開店申請的營業執照網址）同樣缺協定白名單驗證，但當時使用者核准範圍只涵蓋 `logoUrl`，故刻意不擴大該輪修復範圍，僅登記為 `DEF-195`（不排入排程，備註「留待未來另案查證」）。Sprint 156 延伸掃描 URL 協定驗證家族時未重新檢視此項（範圍限定在該輪 grep 命中的欄位，`DEF-195` 早已存在於追蹤表未被重新拾起）。本輪（使用者要求「繼續完成任務」）重新查證此積壓項目。

---

## 2. 查證過程與發現

重新查證 `DEF-195` 記錄時所稱「沒有攻擊鏈」的判斷是否等同於既有「死路徑」判準（`DEF-170`~`177`/`DEF-199`~`201`）：

| 檢查點 | 結果 |
|--------|------|
| 寫入路徑：`TenantService.java:114` 是否讀取並持久化 `request.getBusinessLicenseUrl()` | ✅ 是，寫入 `TenantApplication.businessLicenseUrl` |
| 送出此欄位的端點（`POST /tenant/apply`）本身是否為前端活流程 | ✅ 是——`frontend/src/components/tenant/TenantApplyForm.tsx` 是實際上線的開店申請表單，使用者確實會提交此端點 |
| 官方表單是否暴露 `businessLicenseUrl` 輸入框 | ❌ 否（`grep` 全庫確認 `TenantApplyForm.tsx` 沒有此欄位對應的 `<Input>`） |
| 是否有任何 Response DTO 讀回此值 | ❌ 否（與 Sprint 155 原記錄一致） |

**與既有「死路徑」判準（`DEF-199`~`201`）的關鍵差異**：`DEF-199`~`201` 的整個底層方法／功能（CMS 自訂頁建立、知識庫文章建立、評論圖片上傳）在前端**完全沒有任何呼叫入口**，等同該功能尚未真正上線。而本項的端點 `POST /tenant/apply` 本身**已經是上線中、真實使用者每天在用的活流程**，只是這一個欄位剛好沒有被目前的表單版本使用。這個差異在威脅模型上很關鍵：後端 API 對 `@RequestBody` JSON 反序列化是欄位層級生效，不會因為官方前端表單沒有畫出對應的輸入框就拒收該欄位——任何能呼叫此端點的使用者（即所有能存取開店申請流程的登入使用者）都可以繞過官方表單、直接以 API 呼叫（curl/Postman/瀏覽器開發者工具竄改 request body）夾帶任意字串到 `businessLicenseUrl`。這與 `DEF-104`/`DEF-194`/`DEF-198`（表單本身會用到該欄位、只是沒有下游渲染 sink）在「端點本身可達且會被真實流量觸發」這一點上是相同性質，只是「使用者能否透過官方 UI 送出」的路徑不同（前者是表單本就有欄位、後者是必須繞過表單）。

**結論**：`DEF-195` 不屬於既有死路徑類別，而是與 `DEF-104`/`194`/`198` 同一等級的「寫入路徑活躍、目前無渲染 sink」防禦性缺口，Sprint 155 當時的「範圍外、不擴大修復」純粹是排程紀律考量，非「判斷不需要修」，本輪補回。

---

## 3. 實作內容

### 3.1 DEF-195：`TenantApplicationRequest.businessLicenseUrl` 協定白名單

[`TenantApplicationRequest.java:43-49`](../../backend/src/main/java/com/nextkey/ecommerce/api/dto/TenantApplicationRequest.java#L43-L49) 新增：

```java
@Size(max = BUSINESS_LICENSE_URL_MAX_LENGTH, message = "Business license URL must not exceed 500 characters")
@Pattern(regexp = "^(?!\\s*(?i:javascript|data|vbscript|file):).*$",
        message = "Business license URL must not use javascript/data/vbscript/file protocol")
private String businessLicenseUrl;
```

沿用 `DEF-103`/`104`/`194`/`198` 已驗證過的同一條負向前瞻（negative lookahead）regex 與訊息風格，保持全代碼庫協定黑名單驗證的一致寫法。

---

## 4. 範圍外（刻意不做，如實揭露）

- **未重新查證 `DEF-199`/`200`/`201`**：本輪只針對 `DEF-195` 這一項因「錯誤歸類為死路徑」而重新查證，`DEF-199`~`201` 的「整個功能前端零 UI 入口」判準與本項不同，維持 Sprint 156 的結論不變。
- **未擴大掃描全庫是否還有其他被誤判為死路徑、實際上端點活躍只是欄位未使用的案例**：本輪範圍限定在重新查證 `DEF-195` 這一個已知積壓項目，非窮舉式重新審視所有「不排入排程」項目。

---

## 5. 驗證結果

### 5.1 DEF-195 紅燈先行驗證

新增 `TenantApplicationRequestValidationTest`（11 案例）。修復前執行：`Tests run: 11, Failures: 6`（6 個危險協定案例如預期失敗，證實缺口存在）。套用 `@Pattern` 後重跑：`Tests run: 11, Failures: 0`，流程與 `DEF-103`/`104`/`105`/`194`/`196`/`197`/`198` 一致。

### 5.2 `mvn -o checkstyle:check`

0 違規。

### 5.3 `mvn -o verify`（完整單元＋整合回歸）

**BUILD SUCCESS**：單元測試 **1340 個**（相對 Sprint 156 結束時基準 1329，+11 為本輪新增的 `TenantApplicationRequestValidationTest`），**0 failures / 0 errors**；整合測試 **478 個**（與 Sprint 156 基準持平，本輪未新增整合測試），**0 failures / 0 errors**。

### 5.4 `make validate-e2e`（乾淨 DB + host 全棧 + Playwright）

**62 passed / 4 skipped / 0 failed**，與 Sprint 156 既有基準完全一致（本輪無新增/刪除 E2E 案例，純背景回歸確認），schema 對齊無漂移（`[e2e-gate] ✅ 本地 E2E 守門通過`）。

---

## 6. 下一步 / Action Items

| # | 項目 | 狀態 |
|---|------|------|
| 1 | 查證 `DEF-195` 是否真為死路徑 | ✅ 完成，結論：非死路徑，見 §2 |
| 2 | `DEF-195`：`TenantApplicationRequest.businessLicenseUrl` 協定白名單 | ✅ 完成 |
| 3 | `mvn -o verify` 完整回歸 | ✅ 完成 |
| 4 | `make validate-e2e` | ✅ 完成 |

---

## 7. 誠實揭露總結

- `DEF-195` 在 Sprint 155 被記錄為「不排入排程」，字面上容易被誤讀成「判斷不需要修」，但原文其實寫的是「不擅自擴大本輪修復範圍」——這是排程紀律用語，不是安全判斷結論，本輪重新查證後確認這是真實缺口並補上修復。
- 本輪未對追蹤表中其他「不排入排程」項目做同樣的重新查證，不排除還有類似措辭被誤讀的積壓項目，如實記錄為未探查範圍。
