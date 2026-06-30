# Sprint 26 Retrospective 報告 / Sprint 26 Retrospective Report

> **Sprint 編號**: Sprint 26
> **期間**: 2026-10-11 ~ 2026-10-24 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **報告日期**: 2026-07-01
> **基於**: [SPRINT_26_PLAN.md](../04_planning/SPRINT_26_PLAN.md), [SPRINT_26_REVIEW.md](./SPRINT_26_REVIEW.md), [SPRINT_25_RETRO.md](./SPRINT_25_RETRO.md)

---

## 1. Sprint 26 回顧概覽

| 項目 | 內容 |
|------|------|
| **Sprint 目標** | Sprint 25 成果入庫驗證 + WS/即時品質制度化 + IM/M11 技術債清償 |
| **規劃 SP（承諾）** | 7 SP（P1+P2，US-001~004） |
| **完成 SP** | 9 SP（承諾 7 + Buffer 2，US-001~006 全部完成） |
| **完成 US** | 4/4 承諾 + 2/2 Buffer（100%+） |
| **測試結果** | `@Test` 靜態 668（+9）；catch(Exception)=0、@Deprecated=0；本地 e2e 27 passed/5 skip/0 fail |
| **團隊** | 2 人 Dev Team + AISDLC Agents（PM/BA/SD/Dev/QA + Explore） |
| **計畫外工作** | 本地優先 CI 整套工程（停用雲端自動 CI、validate-e2e/release、pre-push v4→v5、push 降頻） |

### 1.1 重大里程碑

| 事件 | 描述 | 影響 |
|------|------|------|
| ✅ **本地優先 CI 落地** | 三 workflow 改 workflow_dispatch only，雲端日常 CI 整套搬回本地 | 省 private repo Actions 費用、擺脫帳單封鎖卡關 |
| ✅ **e2e 制度化為阻擋性守門** | `make validate-e2e`（host 全棧 + 乾淨 DB + Playwright）strict 預設 | WS/即時功能有了本地端到端防線 |
| ✅ **WS/即時 DoD 制度化（AI-1001）** | REALTIME_ASYNC_E2E_DOD.md 明訂真實 client E2E 為 DoD | Sprint 25 最大教訓制度化 |
| ✅ **M11 取消技術債清零** | DEF-010 一致性 + DEF-011 錯誤碼 + 8 單元測試 | Sprint 25 調查結論落地實作 |
| ✅ **Sprint 25 Action Items 5/5 落地** | AI-1001~1005 全數完成 | 連續性執行 |
| 🟡 **DEF-014「假 bug」釐清** | e2e 乾淨 DB 註冊 401 真因為 validate-e2e.sh 誤設 API_URL（非產品 bug） | 避免誤改產品碼；凸顯守門腳本自身需驗證 |

---

## 2. 做得好的地方（What Went Well）

### 2.1 目標達成

| 項目 | 說明 | 證據 |
|------|------|------|
| **承諾 + Buffer 全完成** | US-001~006 全部達成所有 AC | 9 SP / 100%+ |
| **Sprint 25 Action Items 5/5 落地** | AI-1001~1005 全數完成 | 連續性執行 |
| **技術債維持零** | catch(Exception)=0、@Deprecated=0 維持 | 靜態掃描確認 |

### 2.2 技術實現

| 項目 | 說明 | 影響 |
|------|------|------|
| **本地優先 CI 架構正確** | act -W 無視 on: 過濾（實測），故停用雲端觸發不影響本地驗證 | 雲端零觸發、本地全覆蓋 |
| **守門腳本自身的根因分析** | DEF-014 以 curl 實測釐清為腳本 API_URL bug（雙 /v2），非產品 401 | 沒有盲改產品安全設定（Rule 1/3） |
| **一致性不變量測試** | OrderStateMachineTest 加「轉換表↔canCancel 一致性」不變量，防止 DEF-010 復發 | 測試驗證意圖非僅行為（Rule 9） |
| **jsonb 慣例統一低風險落地** | 先評估改動面（僅 2 placeholder 寫入點）再動工 | 變更面最小（Rule 2/3） |

### 2.3 流程管理

| 項目 | 說明 | 證據 |
|------|------|------|
| **逐單元編譯-測試循環** | 每個 US 完成即 compile + test +（改 schema 時）守門關卡 | 全程 pre-commit 通過，零 --no-verify |
| **依使用者回饋即時調整守門** | pre-push v4（提速）→ v5（上 GIT 必完整測試），兩次都依明確回饋 | 守門設計貼合實際使用成本 |
| **誠實揭露邊界** | 明說「pre-push v5 完整端到端綠燈尚未實測（此 commit 純文件走略過路徑）」 | Rule 12 大聲失敗 |

---

## 3. 需要改善的地方（What Could Be Improved）

### 3.1 流程問題

| 問題 | 根本原因 | 影響程度 |
|------|----------|---------|
| **🟡 開發 backlog 接近見底** | 無正式 product backlog/roadmap，工作全由 Retro AIs + DEF 驅動；活躍 DEF 僅剩 2 個低優先 | 🟡 中 — Sprint 27 起需產品方向決策（新功能 epic 或轉維護模式） |
| **🟡 守門腳本自身無回歸測試** | validate-e2e.sh 的 API_URL bug（DEF-014）潛伏，靠人工 curl 才釐清 | 🟡 中 — 本地守門工具鏈日益關鍵，但自身缺自動驗證 |
| **🟡 pre-push v5 完整守門尚未端到端實測** | 收尾期間的 push 多為純文件，走「略過」路徑，未實際觸發 validate-release（~30-45 分） | 🟡 中 — 下次 backend/frontend push 才會首次實跑，需確認綠燈 |
| **Sprint 排期與真實開發週期持續脫節（第九次）** | 文件排期（10-11~10-24）與實際開發日（07-01）差距約 100+ 天 | 🟢 低 — 慢性問題，不影響交付品質 |

### 3.2 技術問題

| 問題 | 根本原因 | 影響程度 | 處置 |
|------|----------|---------|------|
| **M09 MQ 通知缺端到端驗證** | backend-only 非同步（Producer→Consumer）僅單元/整合，無「觸發→消費→可觀測」e2e | 🟢 低 | DEF-013（Sprint 27，套用 AI-1001 DoD） |
| **前端 next/font/google 建置期外部抓取** | layout.tsx 用 next/font/google，build 期向 fonts.googleapis.com 抓取，離線/網路不穩即失敗 | 🟢 低 | DEF-015（Sprint 27，改 next/font/local） |

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1101 | **產品方向決策** | backlog 接近見底，PM/PO 決定 Sprint 27+ 走向：新功能 epic、既有模組強化、或正式轉「維護/硬化模式」；必要時建立 PRODUCT_BACKLOG/ROADMAP | PM Victoria | **P1** | Sprint 27 |
| AI-1102 | **DEF-015：前端字型本地化** | layout.tsx 改 `next/font/local` 或自帶字型，移除建置期外部網路依賴，使離線 `npm run build` / validate-e2e 穩定 | Dev David | P1 | Sprint 27 |
| AI-1103 | **DEF-013：M09 MQ 通知端到端驗證** | 依 REALTIME_ASYNC_E2E_DOD.md，補「觸發→消費→可觀測結果」端到端測試 | Dev David + QA Quincy | P2 | Sprint 27 |
| AI-1104 | **pre-push v5 完整守門端到端實測** | 下次含 backend/frontend 變更的 push 時，確認 `make validate-release` 實跑綠燈（首次驗證 v5 全流程） | Dev David | P2 | Sprint 27 |
| AI-1105 | **本地守門腳本最小回歸** | 為 validate-*.sh 關鍵前置（如 API_URL 組裝、port 檢查）加最小自我檢查，避免 DEF-014 類腳本 bug 再潛伏 | Dev David | P3（Buffer） | Sprint 27 |

---

## 5. Sprint 25 → Sprint 26 Action Items 追蹤結果

| Action Item | 內容 | Sprint 26 達成狀態 |
|-------------|------|------------------|
| AI-1005 | 帳單解除後 push + 雲端 E2E 驗證 | ✅ 完成（改本地優先策略，US-001） |
| AI-1001 | WS 真實 client E2E DoD 化 | ✅ 完成（US-002） |
| AI-1003 | 後端 toMessageResponse conversationId | ✅ 完成（US-003） |
| AI-1004 | M11 取消技術債清理 | ✅ 完成（US-004） |
| AI-1002 | 本地整合測試 DB make target | ✅ 完成（US-005） |

**Action Items 完成率**: 5/5（100%）

---

## 6. Velocity 趨勢

| Sprint | 規劃 SP | 完成 SP | Velocity |
|--------|--------|--------|---------|
| Sprint 21 | 12 | 12 | 100%（含 Buffer-A） |
| Sprint 22 | 12→8 | 8 | 100%（含 Buffer-A+B） |
| Sprint 23 | 12 | 12 | 100%（含 Buffer-A+B） |
| Sprint 24 | 7（承諾） | 7 | 100%（承諾範圍，Buffer 未動用） |
| Sprint 25 | 9（承諾+Buffer） | 11 | 100%+（承諾 + Buffer 全完成） |
| Sprint 26 | 7（承諾）+2 Buffer | **9** | **100%+（承諾 + Buffer 全完成）** |

> **Sprint 26 觀察**: 承諾 7 SP 全達成 + Buffer 2 SP 完成（= 9 SP）。但本 Sprint 真實重心是**計畫外的本地優先 CI 工程**（停用雲端、validate-e2e/release、pre-push v4→v5），未計入 SP，故帳面 9 SP 低估了實際投入。

---

## 7. 技術債趨勢

| 指標 | Sprint 25 後 | Sprint 26 後 |
|------|------------|------------|
| `catch(Exception)` 生產程式碼 | 0 處 ✅ | **0 處** ✅ |
| `@Deprecated` 生產程式碼 | 0 處 ✅ | **0 處** ✅ |
| `@Test` 方法數（靜態） | 659 | **668** |
| Flyway 最新版本 | V56 | V56（無新 migration） |
| 本地 e2e 守門 | 手動 live E2E | ✅ `make validate-e2e` strict 制度化 |
| 雲端 CI 費用 | push 即觸發雲端 | ✅ 停用自動觸發（本地優先） |
| 活躍 DEF | DEF-009/010/011/012（皆低） | **DEF-013、DEF-015（僅 2 個低優先）** |

> DEF-009/010/011/012/014 本 Sprint 全數清償，活躍 DEF 降至 2 個低優先 —— 技術債極低，但也代表 backlog 見底（見 AI-1101）。

---

## 8. 下一步方向（Sprint 27 預覽）

| 候選項 | 類型 | 預估 SP | 依據 |
|--------|------|--------|------|
| 產品方向決策（新功能/維護模式） | 規劃 | 1 | AI-1101（P1） |
| DEF-015 前端字型本地化 | 技術債 | 1 | AI-1102（P1） |
| DEF-013 M09 MQ 通知端到端驗證 | 品質 | 1 | AI-1103（P2） |
| pre-push v5 完整守門實測 | 流程 | 0.5 | AI-1104（P2） |
| 守門腳本最小回歸 | 流程 | 1 | AI-1105（Buffer） |

> **Sprint 27 重點建議**: 本 Sprint 是「輕量收尾型」。先做 AI-1101（產品方向決策）—— 沒有它，後續 Sprint 將持續無正式 backlog；同時清掉 DEF-015（離線 build 風險）。MQ e2e（DEF-013）與守門實測為次要。

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + BA Beatrice + Dev David + SD Marcus + QA Quincy + Claude Code
