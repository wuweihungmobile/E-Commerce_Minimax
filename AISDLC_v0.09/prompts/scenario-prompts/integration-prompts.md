# Integration 第三方整合快速啟動指令集
# Third-Party Integration Quick Start Prompts

**版本**: v0.09
**適用情境**: Integration - 第三方系統整合
**最後更新**: 2026-02-15

---

## 🚀 一鍵啟動指令

### 標準啟動

```
我需要整合第三方 API/服務。

整合資訊：
- 第三方服務：[服務名稱，如 Stripe/Twilio/Google Maps]
- 整合目的：[支付/簡訊/地圖/等]
- API 文檔：[文檔連結]
- 認證方式：[API Key/OAuth/JWT]

請載入 AISDLC_INIT.md，啟動 integration-analysis-flow，執行 Integration SOP。
```

### 快速 API 評估

```
我想評估某個第三方 API 是否適合整合。

API 資訊：
- API 名稱：[名稱]
- 文檔連結：[URL]
- 替代方案：[其他類似服務]

請 Integration-Specialist (IntegX) 評估：
- API 能力與限制
- 定價方案
- 可靠性與 SLA
- 整合難度
- 產出 Third_Party_API_Analysis
```

---

## 📊 階段推進指令

### 階段 1：API 研究與分析

```
請執行 Integration SOP 階段 1：API 研究與分析。

研究內容：
- API 文檔閱讀（端點/參數/回應格式）
- 認證機制（OAuth/API Key/JWT）
- Rate Limiting（請求限制）
- 錯誤處理（錯誤碼/重試策略）
- Webhook 支援（若有）

產出：Third_Party_API_Analysis
```

### 階段 2：整合設計

```
API 分析已完成，請進入階段 2：整合設計。

請 Integration-Specialist (IntegX) 和 SD-Architect (Marcus) 協作：
- 設計整合架構（直接調用/透過 BFF/使用 Adapter）
- 設計認證流程（Token 管理/刷新機制）
- 設計資料轉換（第三方格式 ↔ 內部格式）
- 設計錯誤處理（Retry/Circuit Breaker/Fallback）
- 設計 Webhook 處理（若適用）

產出：API_Integration_Design
```

### 階段 3：安全性與合規檢查

```
整合設計已完成，請進入階段 3：安全性與合規檢查。

檢查項目：
- 敏感資料處理（API Key/Token 存儲）
- 資料傳輸加密（HTTPS/TLS）
- 資料隱私（GDPR/個資法合規）
- 第三方資料留存政策
- 審計日誌（API 調用記錄）

產出：Security_Compliance_Checklist
```

### 階段 4：Mock 與測試環境準備

```
安全檢查已完成，請進入階段 4：Mock 與測試環境準備。

請準備：
- Mock Server（模擬第三方 API 回應）
- Sandbox 環境（第三方提供的測試環境）
- 測試資料（測試用的 API Key/帳號）
- 錯誤場景模擬（網路失敗/Timeout/Rate Limit）

產出：Integration_Test_Environment_Setup
```

### 階段 5：實作與整合測試

```
測試環境已準備，請進入階段 5：實作與整合測試。

實作內容：
- API Client 實作（HTTP Client/SDK）
- 認證模組（Token 管理/刷新）
- 資料轉換層（Adapter/Mapper）
- 錯誤處理（Retry/Circuit Breaker）
- Webhook Endpoint（若適用）

測試：Integration Test Plan
```

### 階段 6：監控與告警設定

```
整合實作已完成，請進入階段 6：監控與告警設定。

請 DevOps-Engineer 設定：
- API 調用監控（成功率/延遲/錯誤碼分佈）
- Rate Limit 監控（避免超限）
- 告警規則（高錯誤率/Timeout）
- Dashboard（整合狀態一覽）

產出：Monitoring_Setup
```

### 階段 7：文檔與維運手冊

```
監控已設定，請進入階段 7：文檔與維運手冊。

請 Technical-Writer 產出：
- 整合架構文檔
- API 調用文檔（內部使用）
- 故障排查手冊（Runbook）
- 第三方變更因應計畫

產出：Integration_Documentation
```

---

## 🔄 常見變體指令

### 變體 1：支付整合（Payment Gateway）

```
我要整合支付服務（Stripe/PayPal/綠界）。

整合需求：
- 支付方式：[信用卡/行動支付/超商代碼]
- 金流方式：[直接/間接]
- 合規要求：[PCI-DSS]

請執行 Integration SOP，特別注意：
- PCI-DSS 合規（不存儲信用卡資訊）
- 3D Secure 驗證
- Webhook 處理（付款成功/失敗）
```

### 變體 2：OAuth 第三方登入

```
我要實作 OAuth 第三方登入（Google/Facebook/Line）。

整合需求：
- OAuth Provider：[Google/Facebook/Line]
- Scope：[需要的權限]
- 用戶資料同步：[是/否]

請執行 Integration SOP，特別注意：
- OAuth 2.0 流程（Authorization Code Flow）
- Token 管理（Access Token/Refresh Token）
- 用戶資料對應（第三方 ID ↔ 內部 ID）
```

### 變體 3：地圖服務整合

```
我要整合地圖服務（Google Maps/Mapbox）。

整合需求：
- 功能：[地圖顯示/地址搜尋/路線規劃]
- 使用量：[預估每日請求數]
- 預算：[X 元/月]

請評估：
- API Quota 是否足夠
- 定價方案比較
- 替代方案（開源地圖）
```

### 變體 4：簡訊/Email 服務

```
我要整合簡訊或 Email 發送服務（Twilio/SendGrid）。

整合需求：
- 服務類型：[簡訊/Email]
- 使用場景：[驗證碼/通知/行銷]
- 發送量：[X 則/天]

請執行 Integration SOP，特別注意：
- Template 管理
- 發送狀態追蹤（Delivery Status）
- Bounce/Complaint 處理
```

### 變體 5：Analytics 追蹤整合

```
我要整合 Analytics 服務（Google Analytics/Mixpanel）。

整合需求：
- 追蹤事件：[頁面瀏覽/按鈕點擊/轉換]
- 追蹤平台：[Web/Mobile App]

請執行 Integration SOP，特別注意：
- 隱私合規（Cookie Consent/GDPR）
- 事件命名規範
- 自定義維度/指標
```

---

## 🆘 疑難排解指令

### 問題 1：API 文檔不清楚

```
第三方 API 文檔寫得不清楚，難以理解。

請 Integration-Specialist (IntegX) 協助：
- 閱讀文檔並整理關鍵資訊
- 透過範例推斷 API 行為
- 實際測試驗證（使用 Postman/cURL）
- 產出清晰的整合說明
```

### 問題 2：認證失敗

```
第三方 API 認證一直失敗。

錯誤資訊：[提供錯誤訊息]

請診斷：
- API Key/Secret 是否正確
- 認證流程是否正確（OAuth Flow）
- Header/Parameter 是否遺漏
- 時區/時間戳是否正確（某些 API 需要）
```

### 問題 3：Rate Limit 問題

```
經常觸發 Rate Limit，導致請求失敗。

目前狀況：
- Rate Limit：[X 次/分鐘]
- 實際請求量：[Y 次/分鐘]

請優化：
- Request Batching（批次請求）
- Caching（減少重複請求）
- Request Queue（請求佇列平滑化）
- 升級方案（若必要）
```

### 問題 4：Webhook 不穩定

```
Webhook 經常漏掉或重複接收。

問題：
- 漏掉事件
- 重複接收
- 接收延遲

請改善：
- Webhook Endpoint 穩定性（Timeout 設定）
- 冪等性設計（處理重複事件）
- 事件補償機制（主動查詢）
```

---

## 🎓 進階使用技巧

### 技巧 1：API 降級與 Fallback

```
第三方 API 可能不穩定，需要降級方案。

請設計：
- Circuit Breaker（熔斷器）
- Fallback 策略（使用快取/預設值）
- Graceful Degradation（功能降級）
```

### 技巧 2：多供應商支援（Multi-Provider）

```
我想支援多個第三方服務（如多個支付閘道）。

請設計：
- Adapter Pattern（統一介面）
- Provider 切換機制（A/B Testing）
- Fallback Chain（主供應商失敗切換到備援）
```

### 技巧 3：成本監控

```
我想監控第三方 API 使用成本。

請設定：
- 請求量監控（接近 Quota 時告警）
- 成本估算（依定價方案計算）
- 預算告警（超過預算時通知）
```

---

## 📚 參考資源

### 相關文檔
- [Integration SOP](../../scenarios/integration/SOP.md)
- [AISDLC_INIT.md](../../AISDLC_INIT.md)

### 相關 Workflows
- [integration-analysis-flow.md](../../workflow/scenario-specific/integration-analysis-flow.md)

### 相關 Agents
- [integration-specialist-zh.yaml](../../agent/specialized/integration-specialist-zh.yaml) - IntegX
- [sd-architect-zh.yaml](../../agent/core/05.sd-architect-zh.yaml) - Marcus
- [devops-engineer-zh.yaml](../../agent/specialized/devops-engineer-zh.yaml) - DevOps Engineer

### 文檔模板
- [整合文檔模板](../../docs_template/scenario_specific/integration/) 🚧 (模板 v0.09+ 預留)

---

**版本**: v0.09
**維護者**: AISDLC Framework Team
**最後更新**: 2026-02-15
