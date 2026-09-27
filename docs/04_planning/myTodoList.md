# 我的待辦清單（需人工操作，AI 做不到）

> **建立日期**: 2026-09-27
> **來源**: Sprint 203～209 收尾彙整（`DEFERRED_ITEMS_TRACKER.md`、`SPRINT_206~209_PLAN.md`、`STRIPE_PRODUCTION_CHECKLIST.md`）
> **性質**: 以下項目都需要存取實際部署環境、Stripe Dashboard 或 Email 帳號密碼，AI 無法代為操作，故彙整於此追蹤。
> **目前程式狀態**: Sprint 203～209 已全數 push，`origin/main` = `51c4739`，雲端 CI 全綠。

---

## 1. 真實寄信服務收尾（Sprint 209，優先）

Sprint 209 已接上 Google Workspace SMTP 的程式碼（`SmtpEmailSender`），但**沒有真的送出過一封信**，因為沒有帳密。要讓「忘記密碼」「Email 驗證」真正可用，需要：

- [ ] 確認該 Google Workspace 帳號已開啟**兩步驟驗證**（應用程式密碼的前提）
- [ ] 產生一組**應用程式密碼**（不是帳號登入密碼）
- [ ] 在部署環境設定以下環境變數：
  - `SMTP_USERNAME`＝該 Workspace 帳號（例如 `noreply@yourdomain.com`）
  - `SMTP_PASSWORD`＝上一步產生的應用程式密碼
  - `SMTP_HOST`（預設 `smtp.gmail.com`，Workspace 通常免改）
  - `SMTP_PORT`（預設 `587`，通常免改）
  - `SMTP_FROM_ADDRESS`（選填，預設沿用 `SMTP_USERNAME`）
- [ ] 設定完成後，**手動跑一次忘記密碼流程**，確認真的收得到信——這是唯一能驗證這輪修復有沒有效的方式，AI 做不到
- [ ] 若貴公司 Workspace 管理員**停用了應用程式密碼**，這套機制會整個不能用，需要改走 OAuth2（屬於另一輪工作，屆時再提出）

---

## 2. 維運三件事（跨多個 Sprint 累積，仍在你手上）

三項都需要存取實際部署環境才能確認，AI 無法代為操作：

- [ ] **前方代理是否有送 `X-Forwarded-Proto`**：確認 Nginx/負載平衡器等前方代理有正確轉送此標頭，後端才能正確判斷請求是否為 HTTPS（影響 HSTS 標頭是否送出）
- [ ] **Staging 環境首次上線後，檢查瀏覽器主控台有無 CSP 違規**：前端已啟用嚴格 nonce CSP（84 條路由全動態），首次上線需實際打開瀏覽器 DevTools 觀察有無被攔截的資源
- [ ] **確認 Sprint 198 的 HSTS 設定是否已部署過**：若已經部署過帶 `includeSubDomains` 的 HSTS，需要額外送一次 `max-age=0` 才能讓瀏覽器撤回舊的 HSTS 設定（否則子網域可能被舊設定影響）

---

## 3. Stripe 測試模式端到端走查

完整檢查清單見 [STRIPE_PRODUCTION_CHECKLIST.md](../08_deployment/STRIPE_PRODUCTION_CHECKLIST.md)，其中 **§D 全部項目**都需要人工登入 Stripe Dashboard 操作，AI 無法代為驗證。特別提醒最新（Sprint 207，DEF-288）的一項：

- [ ] **部分退款連續退兩次驗證**：對同一筆訂單，在 24 小時內連續觸發兩次部分退款（建議一次金額不同、一次金額相同，例如先退 100 再退 100），到 Stripe Dashboard 確認出現**兩筆**退款、金額各如預期
  - 背景：原本的退款冪等鍵對同一筆付款恆相同，導致同一筆訂單第二次退款可能被 Stripe 拒絕或視為重送而不建立新退款；程式已修復（鍵改綁「付款意圖＋退款前累計已退額＋本次金額」），**但這個修復只在本機以 WireMock 模擬驗證過，從未對真實 Stripe 驗證**
  - 若兩次退款在 Dashboard 只看到一筆，代表修復無效，請回報
- [ ] 其餘 §D 項目（付款成功／失敗、全額退款、Connect onboarding、分潤撥款 Transfer、`transfer.reversed`）依 Checklist 逐項走查並填寫 §G 走查結果記錄

---

## 待辦來源對照（供追蹤，非待辦事項本身）

| 項目 | 狀態 | 來源 Sprint |
|------|------|------------|
| DEF-287 結算單手動觸發入口 | ✅ 已修復 | Sprint 208 |
| DEF-285 PRD 冪等規範決策 | ✅ 已拍板並落實 | Sprint 208 |
| DEF-284 E_1005 狀態碼 | ✅ 已修復 | Sprint 208 |
| DEF-288 Stripe 退款冪等鍵 | ✅ 已修復（程式面），待你在 Stripe 測試模式驗證 | Sprint 207 |
| 真實寄信服務接入 | ✅ 程式已接上，待你提供憑證與手動驗證 | Sprint 209 |
| 維運三件事 | ⏳ 待你操作部署環境 | Sprint 202、205、206、207、208 反覆記錄 |
| Stripe 測試模式走查 | ⏳ 待你操作 Stripe Dashboard | 長期累積於 `STRIPE_PRODUCTION_CHECKLIST.md` |
