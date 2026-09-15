# Sprint 160 Plan — 延伸 Sprint 159 §4 範圍外聲明：`Map<String,...>` 型別請求鍵值層級風險掃描，意外發現 Stripe Webhook 完全不可達（`DEF-202`）

**Sprint**: Sprint 160
**日期**: 2026-09-15

---

## 1. 起點

[SPRINT_159_PLAN.md](SPRINT_159_PLAN.md) §4 明確列出三項範圍外聲明，其中第二項是「`Map<String, Boolean>`/`Map<String, Object>` 的鍵值本身是否有上限或格式驗證（`AdminController.updateTenantFeatureToggle`/`StripeWebhookController`）：這 2 處確認皆有其他把關機制……未逐一複查是否有更細緻的鍵值格式風險」。本輪（使用者要求「繼續完成任務」）接續此角度深入查證。

---

## 2. 查證過程與結論

### 2.1 全庫重新枚舉 `@RequestBody Map<...>` 端點

`grep -rn "@RequestBody" --include="*Controller.java" | grep -iE "Map<"` 找出 **3 處**（Sprint 159 §4 僅提及 2 處，本輪枚舉補上遺漏的第 3 處）：

| # | 位置 | 型別 | Sprint 159 是否提及 |
|---|------|------|---------------------|
| 1 | `AdminController.updateTenantFeatureToggle` | `Map<String, Boolean>` | ✅ 有提及 |
| 2 | `TenantController.updateMemberRole` | `Map<String, String>`（帶 `@Valid`） | ❌ **未提及，本輪新枚舉出** |
| 3 | `StripeWebhookController.handleLinePayWebhook` | `Map<String, Object>` | ✅ 有提及 |

### 2.2 逐一查證

| # | 查證內容 | 結論 |
|---|----------|------|
| 1 | `AdminController.updateTenantFeatureToggle`：`request.get("enabled")` 為 `null`（缺鍵）時，`AdminService.updateTenantFeatureToggle` 是否會出錯或造成資料不一致 | ✅ CLEAN。`toggle.setIsEnabled(enabled != null ? enabled : false)` 已對 null 做防禦性處理，缺鍵僅靜默停用 feature（非崩潰、非資料錯亂）；端點本身要求 `SUPER_ADMIN`，`feature` 路徑變數需對應既有 toggle 列才能通過，非任意鍵值皆可寫入 |
| 2 | `TenantController.updateMemberRole`：`@Valid` 標註在 `Map<String, String>` 上，但 Bean Validation 對純 `Map<String,String>` 的值沒有任何可級聯的約束（`String` 本身無驗證 annotation），此標註是**誤導性的裝飾性寫法**（沒有實際驗證效果）；真正的邊界檢查何在？ | ✅ CLEAN（但發現誤導性標註）。`TenantService.updateMemberRole` 對 `request.get("role")` 執行 `TenantMember.StoreRole.valueOf(newRole)`，非法字串會拋 `IllegalArgumentException` 並轉為 `BusinessException(E_1001)`，實際驗證是**列舉白名單**而非 Bean Validation，`@Valid` 純屬裝飾、不構成安全缺口，但容易誤導後續維護者以為此處有 Bean Validation 規則在把關。因非安全缺口、純程式碼清晰度問題，比照 `DEF-025`/`DEF-193` 判準只記錄不修改（見 §6） |
| 3 | `StripeWebhookController`：`/stripe`（簽章驗證）與 `/linepay`（**零簽章驗證**的 stub，僅記錄 log 後回 2xx）在 `SecurityConfig.authorizeHttpRequests()` 的保護層級是否一致 | 🔴 **發現嚴重缺陷，見 §3**：`/v2/payments/webhook/**` 完全未被列入 `permitAll()`，落在預設規則 `.anyRequest().authenticated()` 之下。Stripe 伺服器的真實 webhook 回呼**不會攜帶 JWT**（僅有 `Stripe-Signature` header），故此端點對外部呼叫方而言**恆為 401，完全不可達**——與「/linepay 零簽章驗證」的問題方向相反：不是保護不足，而是保護機制用錯（JWT 認證套用在一個永遠不會有 JWT 的伺服器對伺服器端點上），導致功能完全失效 |

---

## 3. `DEF-202`：Stripe Webhook 因缺 `permitAll()` 規則完全不可達（生產影響：付款成功確認以外的所有非同步事件無法同步）

### 3.1 根因

`SecurityConfig.authorizeHttpRequests()`（`backend/src/main/java/com/nextkey/ecommerce/api/config/SecurityConfig.java`）逐一為需要公開存取的端點加上 `permitAll()`（如 `/v2/posts`、`/v2/cms/banners/active`、`/ws/**`），其餘一律落入 `.anyRequest().authenticated()`。`/v2/payments/webhook/**`（`StripeWebhookController`）從未被加入這份清單。

### 3.2 實測驗證（紅燈先行）

新增 `StripeWebhookReachabilityTest`，以 `@SpringBootTest` + `@AutoConfigureMockMvc` 走完整 Security Filter Chain（非 `@WebMvcTest` 的簡化鏈路），對 `POST /v2/payments/webhook/stripe`（無 `Authorization` header，符合 Stripe 真實回呼樣態）送出請求：

- **修復前**（`git stash` 暫時移除 `SecurityConfig` 改動後重跑）：實測回應 **401**，`{"success":false,"code":"E-1000","message":"Authentication required"}`——在 `JwtAuthenticationFilter`/`AuthorizationFilter` 這層就被擋下，**請求從未進入 `StripeWebhookController`**。
- **修復後**：實測回應 **200**（測試環境 `stripe.webhook-secret` 留空，簽章驗證依設計跳過「測試模式」，直接進入 `PaymentWebhookService.handleEvent()`）。

### 3.3 業務影響範圍查證

逐一追蹤 `PaymentWebhookService.dispatch()` 處理的 5 種事件類型，確認是否有非 webhook 的同步備援路徑：

| 事件類型 | 對應方法 | 是否有同步備援路徑 |
|---------|---------|---------------------|
| `checkout.session.completed`（付款成功） | `markStripePaymentSucceeded` | ✅ 有——`PaymentStateService.confirmStripeCheckout()`（買家結帳完成後瀏覽器 return URL 導回時，以自己的 JWT 呼叫已認證端點，主動 retrieve session 狀態並標記成功，Sprint 50 AI-2410 Phase A）。故此事件在買家正常完成結帳的路徑上**不受影響**（return URL 已能標記成功），但透過 webhook 的**冗餘保障**（例如買家結帳成功後未導回、直接關閉分頁的情境）完全失效 |
| `payment_intent.payment_failed` | `markStripePaymentFailed` | ❌ 全庫 `grep` 確認 `markStripePaymentFailed` 僅此一個呼叫點，**無任何同步備援** |
| `charge.refunded`（退款，含 Stripe Dashboard 直接操作的退款） | `markStripeRefunded` | ❌ 全庫 `grep` 確認 `markStripeRefunded` 僅此一個呼叫點，**無任何同步備援**——本地 `payments`/`orders` 表永遠不會得知退款已發生，帳實不符 |
| `account.updated`（賣家 Stripe Connect KYC/啟用狀態） | `TenantStripeConnectService.syncAccountStatusFromWebhook` | ❌ 無同步備援，賣家在 Stripe 完成 KYC 後本地狀態不會更新 |
| `transfer.reversed`（撥款被撤銷） | `TransferService.handleTransferReversedWebhook` | ❌ 無同步備援 |

**結論**：5 種事件中 4 種（付款失敗、退款、Connect KYC 同步、撥款撤銷）**完全依賴此 webhook、無任何備援**，webhook 不可達代表這 4 類狀態變化永遠不會反映到本地系統。屬於功能完全失效等級的缺陷，非邊緣情況。

### 3.4 修復

`SecurityConfig.java` 新增一條精準規則：

```java
.requestMatchers(HttpMethod.POST, "/v2/payments/webhook/stripe").permitAll()
```

**刻意只放行 `/stripe`，不放行 `/linepay`**：

- `/stripe` 有 `StripeSignatureVerifierService`（HMAC-SHA256）在 controller 內把關，`permitAll()` 只是把「認證方式」從 JWT 改為 webhook 慣用的 signature-based 認證，架構上正確，且無此規則就永遠無法運作。
- `/linepay` 是零串接的 stub（註解已明載「LinePay 整合預留（stub）」），完全沒有簽章驗證機制；若一併 `permitAll()`，會開放一個毫無防護的公開端點卻無任何業務效益（目前僅記錄 log 後回 2xx）。維持其需要驗證（實質上等同不可達）不影響任何現有功能，且避免無謂擴大攻擊面。此為刻意決策，留待未來真正串接 LinePay、補上簽章驗證機制時再一併處理 `SecurityConfig` 規則（見 §6 `DEF-203`）。

---

## 4. 範圍外（刻意不做，如實揭露）

- **`TenantController.updateMemberRole` 的 `@Valid` 誤導性標註**：純程式碼清晰度問題（見 §2.2 第 2 項），非安全缺口，比照 `DEF-025`/`DEF-193` 先例只記錄（`DEF-203`……不，見下方 §6 編號），不修改程式碼。
- **`/v2/payments/webhook/linepay` 補上真正的簽章驗證機制**：LinePay 尚未真正串接（無 webhook secret 設定、無簽章演算法實作），屬全新功能開發而非防禦性修復，超出本輪範圍，留待真正開發此整合時處理。
- **`AdminController`/`StripeWebhookController` 以外，全庫是否還有其他「伺服器對伺服器回呼」端點遺漏 `permitAll()`**：本輪已用 `grep -rln "webhook\|callback\|Callback" --include="*Controller.java" -i` 確認全庫僅 `StripeWebhookController` 一處符合此樣式，故此類缺陷已窮盡；但未擴大檢查是否有其他类型的「應公開但被誤鎖」端點（例如未來新增的第三方回呼）。

---

## 5. 驗證結果

- 新增 `StripeWebhookReachabilityTest`（2 案例：`/stripe` 修復後不再 401、`/linepay` 刻意維持需要驗證），紅燈先行證實修復前 `/stripe` 案例失敗（實際 401 ≠ 預期 200）、修復後兩案例皆綠。
- `checkstyle:check`（main + test）：0 違規。
- `mvn -o verify` **1342 個單元測試（+2）+ 478 個整合測試，0 failed**，PMD 無新增違規，`BUILD SUCCESS`（8m06s）。
- `make validate-e2e`：**62 passed / 4 skipped / 0 failed**（3.0m），schema 對齊無漂移，與既有基準一致（本輪 `SecurityConfig` 改動未影響任何既有 E2E 流程）。

---

## 6. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-202`（已修復）：Stripe webhook 因 `SecurityConfig` 缺 `permitAll()` 完全不可達。
- 新增 `DEF-203`（不排入排程，純記錄）：`TenantController.updateMemberRole` 的 `@Valid` 標註在 `Map<String,String>` 上無實際驗證效果，屬誤導性寫法，真正的驗證由 `TenantService` 的列舉白名單把關，非安全缺口。

---

## 7. 誠實揭露總結

- 本輪起點是接續 Sprint 159 §4 的「範圍外聲明」，非追蹤表既定項目（追蹤表本身仍無待排程項目）。
- Sprint 159 對 `Map` 型別端點的枚舉本身有遺漏（只提及 2 處，實際 3 處），本輪先做了枚舉層面的補正,再深入查證。
- `DEF-202` 是本輪查證過程中的意外發現，不在原定「鍵值層級風險」範疇內（根因是 Security 層級的路徑匹配缺漏，不是 Map 鍵值本身的格式/上限問題）；記錄於此以示查證範疇經常會比最初設想的角度更廣。
- 未驗證正式生產環境（Railway/雲端部署）目前是否已因此問題導致真實 Stripe webhook 事件遺失；本輪僅能確認程式碼/設定層面的缺陷與本地環境的實測結果，無法回溯查證歷史上是否已造成實際退款/KYC 資料遺漏（若需要，應另行查詢生產環境的 Stripe Dashboard webhook 送達紀錄與本地 `processed_stripe_events`/`payments` 表是否有落差，超出本輪 `docs/`+程式碼查證範圍）。
- 未逐一複查 `TenantController.updateMemberRole` 以外，全庫是否還有其他「驗證標註存在但對該型別實際無效」的裝飾性 `@Valid` 案例（例如標註在其他無法被 Bean Validation 級聯的容器型別上）。
