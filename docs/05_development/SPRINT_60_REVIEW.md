# Sprint 60 Review / Sprint 60 評審會議

> **Sprint 編號**: Sprint 60
> **期間**: 2028-01-30 ~ 2028-02-12
> **評審日期**: 2026-07-04
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 全站 BusinessException 英文訊息碼化——ErrorCode 中文化 + 動態細節不外洩

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | 全站錯誤訊息中文化（AI-2418）| 8 | ✅ 完成 |

**8 SP 全數完成**。PO 決策完整修復後，全站使用者可見的錯誤訊息不再出現英文字面值。

---

## 2. 交付內容

- **`ErrorCode.java`**：全部 130 個常數的 `message` 翻譯為繁體中文（含 3 個帶 `%d`/`%s` 格式化佔位符的訊息，格式化語意不變）。
- **`BusinessException`**：新增 `getUserMessage()`，回傳 `errorCode.getMessage()`（不含 details/自訂 message 動態細節）；既有 `getMessage()`（含細節，供伺服器端 log 使用）行為不變。
- **`GlobalExceptionHandler`**：`handleBusinessException` 改用 `ex.getUserMessage()` 組成 API 回應；另外 7 處硬編碼英文字串（Validation failed 等）翻譯為中文。
- **測試更新**：`AdminControllerE2ETest`/`BookingControllerE2ETest`/`OrderControllerE2ETest`/`PostControllerE2ETest` 共 4 處依賴舊英文訊息的 API 層級斷言更新為中文；經全面搜尋確認其餘 19 處 `.getMessage()).contains(...)` 斷言檢查的是 details 部分（未受影響，因 `getMessage()` 語意不變）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 後端單元（`mvn test`）| ✅ **422 tests，0 fail** |
| 後端完整整合（`mvn verify -Pintegration-test`，含 failsafe `*IntegrationTest.java`/`*E2ETest.java`）| ✅ **全量 890 tests，0 fail** |
| schema 漂移守門（`make validate-schema`）| ✅ 無漂移（schema-free）|
| 前端變動 | 無（前端已顯示 `response.data.message`，不需改前端程式碼即可看到中文效果）|
| catch(Exception) / @Deprecated 計數 | ✅ 維持 0 |

---

## 4. 誠實揭露（Rule 12）

1. **未逐一修改 383 個呼叫點的動態英文 details 字面值本身**：本次架構性修改（`getUserMessage()` 隱藏 details）讓使用者不再看到英文細節，但程式碼中的英文字串本身（如 `"Room is not open for booking on " + date`）仍存在，只是不再回傳給前端、僅用於伺服器端 log。若未來想在使用者訊息中保留具體細節（例如「哪一天」），需要另外設計結構化錯誤欄位方案，不在本次範圍。
2. **本次全面搜尋並驗證了測試影響範圍**：先廣泛 grep 全部 `.getMessage()`/`"message"` 相關斷言，區分「檢查 details 的內部測試斷言」（19 處，不受影響）與「檢查 API 回應層級的斷言」（4 處，需更新），逐一確認後才動手修改，避免遺漏或誤改。
3. **本次使用正確的 `mvn verify -Pintegration-test` 做全量回歸**：吸取 Sprint 54~58 誤用 `mvn test` 導致漏測的教訓，本 Sprint 全程使用正確指令驗證，890 tests 0 fail 為真正涵蓋 `*IntegrationTest.java`/`*E2ETest.java` 的完整回歸結果。

---

## 5. Demo 重點

- **中文錯誤訊息**：任何觸發 `BusinessException` 的 API 呼叫（如查詢不存在的訂單）現在回傳 `message: "找不到訂單"` 而非 `"Order not found"`。
- **動態細節不外洩**：如超窗擋訂場景，使用者看到的是通用的「房源未開放預訂」中文訊息，而非包含具體日期的英文字串；具體日期仍記錄在伺服器 log 供除錯。

---

**文件版本**: v1.0
**建立日期**: 2026-07-04
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
