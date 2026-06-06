# Sprint 16 Retrospective 報告 / Sprint 16 Retrospective Report

> **Sprint 編號**: Sprint 16
> **期間**: 2026-06-04 ~ 2026-06-06 (實際執行,含 JPA 衝突修復)
> **原訂期間**: 2026-07-13 ~ 2026-07-24 (計劃期間)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **報告日期**: 2026-06-06
> **基於**: [SPRINT_16_PLAN.md](../04_planning/SPRINT_16_PLAN.md), [SPRINT_16_TASKS.md](SPRINT_16_TASKS.md), [SPRINT_16_CODE_REVIEW.md](../06_quality/SPRINT_16_CODE_REVIEW.md), [SPRINT_16_FINAL_APPROVAL.md](../06_quality/SPRINT_16_FINAL_APPROVAL.md)

---

## 1. Sprint 16 回顧概覽

| 項目 | 內容 |
|------|------|
| **Sprint 目標** | 補足 Sprint 15 P0 測試缺口 (4.5 SP) + M08 多圖評價 (6 SP) + 流程改進 (1.5 SP) |
| **完成 SP** | 12 SP (100%) |
| **完成 US** | 8 US (100%) |
| **代碼變更** | 38 檔案 (含 4 個新檔 + 1 個重構 + 2 個刪除),~1,500 行新增 |
| **Migration 變更** | V37 (ReviewReply 表) |
| **JPA 衝突修復** | 1 個架構性重構 (cms.MediaAsset 為主) |
| **團隊** | 2 人 Dev Team |

### 1.1 重大里程碑

| 事件 | 描述 | 影響 |
|------|------|------|
| ✅ **Sprint 16 Final Approval** | 四方審議全數 APPROVED (2026-06-05) | 完成 Sprint 16 8/8 US |
| 🔴 **JPA 衝突發現** | `media.MediaAsset` 與 `cms.MediaAsset` 對應同一 table | 揭露 Sprint 10 隱藏 3 個月的技術債 |
| ✅ **JPA 衝突修復** | 保留 cms,刪除 media 重複 Entity/Repository | 解決長期阻塞測試啟動問題 |
| ✅ **Sprint 15 Release 已合併** | PR #13 + #14 已 MERGED,合併到 main (commit 401d8e2) | Sprint 15 程式碼進入 Production |
| 🔴 **Sprint 16 Release 待執行** | 目前無 release/v2026.06.06-01 分支,develop 領先 main 5 commits | 需建立 release 分支並合併 |

---

## 2. 做得好的地方 (What Went Well)

### 2.1 目標達成

| 項目 | 說明 | 證據 |
|------|------|------|
| **100% US 完成** | 8 個 User Story 全部達成 AC | US-001/002/003/004/005/006/007/008 全部 ✅ |
| **100% SP 達成** | 12 SP 全部完成（規劃容量 18 SP，填充率 67%） | Sprint 容量管理得當,保留 6 SP buffer |
| **M08 評價擴展完成** | 9 張上限 + 圖片管理 API | US-005/006 完整實作 |
| **M07 結算強化** | SettlementService 拆分 + 完整 UT/IT | 4 個專責元件 + 11/14 個 UT |
| **Pre-commit Hook 雙端** | Frontend husky + Backend hooks | Sprint 15 的 2 個 CI 修復不再重演 |

### 2.2 技術實現

| 項目 | 說明 | 證據 |
|------|------|------|
| **SettlementService 拆分** | 從 331 行拆分為 4 個專責元件 (SettlementCalculator/Generator/Reviewer/Service) | TI-002 完成 |
| **多圖 9 張上限雙重防護** | @Size(max=9) Bean Validation + Service 業務驗證 | US-005 雙層防護 |
| **圖片管理 API** | POST/DELETE/PUT 3 個端點 + 權限驗證 | US-006 完整實作 |
| **Scheduled Job 實測** | 6 個本地測試 + Staging Runbook | US-003 替代方案合理 |
| **M15 Media 整合** | existsMediaById 驗證圖片 URL 有效性 | ReviewService 整合 M15 |
| **Flyway V37 Migration** | review_replies 表 + 索引齊全 | ON DELETE CASCADE |
| **既有多圖功能向後相容** | `/reply` 端點 @Deprecated 保留 | 避免破壞性變更 |
| **E_1086, E_1088-1091 錯誤碼** | 統一在 Review 區段命名 | ErrorCode 集中管理 |

### 2.3 流程管理

| 項目 | 說明 | 證據 |
|------|------|------|
| **Commit 訊息規範** | 採用 Conventional Commits | `feat(M08):`, `test(M08):`, `refactor(M07):` |
| **任務分解細緻** | 每個 US 都有明確的 Entity/DTO/Service/Controller 階段 | SPRINT_16_TASKS.md |
| **文件驅動開發** | Plan + Tasks + Code Review + Final Approval 完整產出 | docs/ 結構完整 |
| **四方獨立審議** | Architect / SA / SD / QA 全 APPROVED | SPRINT_16_FINAL_APPROVAL.md |
| **Pre-commit Hook 即時防護** | Frontend husky + Backend hooks 預防 CI 失敗 | Sprint 15 的 2 個 CI 修復不再重演 |

### 2.4 測試品質

| 項目 | 說明 | 證據 |
|------|------|------|
| **Sprint 16 新增測試** | 29 個測試 100% 通過 (ReviewReply 10 + Settlement 14+11 + M07 IT 8 + M08 IT 5 + Job IT 6) | mvn test 驗證 |
| **覆蓋率達標** | ReviewReplyService ~85%, SettlementGenerator ~90%, ReviewService 圖片管理 ~80% | 超過 80% 目標 |
| **測試設計品質** | 邊界測試、異常路徑、整合測試完整覆蓋 | @DisplayName 清楚標示 |

---

## 3. 需改進的地方 (What Could Be Improved)

### 3.1 技術面 (P0 緊急)

| 問題 | 影響 | 建議 | 優先級 |
|------|------|------|--------|
| **🔴 media.MediaAsset 與 cms.MediaAsset 雙重 Entity** | 兩個 Entity 對應同一 `media_assets` table,造成 JPA 衝突,94 個測試 ApplicationContext 載入失敗 | **本次 Sprint 已修復** (保留 cms,刪除 media);Sprint 17 應將 `cms.MediaAsset` 的欄位擴展記錄在 SD 文件 | P0 |
| **🔴 V13/V22 Migrations 從未執行** | `spring.flyway.enabled: false`,Hibernate 自動管理 schema,但 V13/V22 對應的欄位從未同步 | 確認 V22 欄位(category_id, tags, alt_text 等)是否需要正式 Migration,或繼續依靠 Hibernate auto-update | P0 |
| **SettlementService 仍偏大** | 拆分後 Service 層仍有部分複雜邏輯 | 持續觀察,如再增長應再拆分 | P2 |

### 3.2 流程面 (P0 重要)

| 問題 | 影響 | 建議 | 優先級 |
|------|------|------|--------|
| **🔴 Final Approval 測試覆蓋不完整** | SPRINT_16_FINAL_APPROVAL.md 只列 74 個測試 (Sprint 16 新增),實際專案有 489 個測試 | **Sprint 17 起 Final Approval 必須跑完整 mvn test,不能只看新增測試** | P0 |
| ✅ **Sprint 15 Release 已合併** (2026-06-06 確認) | release/v2026.06.04-01 已通過 PR #13 + #14 合併到 main (commit 401d8e2) | Sprint 15 程式碼已進入 Production | (已解決) |
| **🔴 Sprint 16 Release 仍未執行** | 目前無 release/v2026.06.06-01 分支,develop 領先 main 5 commits | **Sprint 17 Day 1 必須建立 release 分支並合併,不能再次跳過** | P0 |

### 3.3 技術債 (P1 重要)

| ID | 問題 | 影響 | 評估 |
|----|------|------|------|
| **TD-001** | 83 個既有測試 bug | M07PaymentMockIntegrationTest 等 8 個測試用 `doNothing()` 對非 void 方法,OrderService.updateOrderStatus 簽名問題等 | 長期未被發現,需 Sprint 17 修復 |
| **TD-002** | cms.MediaService 299 行 | 檔案偏大,內含 StorageService 整合、檔案大小/MIME 驗證邏輯 | 可拆分為 MediaUploadService + MediaValidationService |
| **TD-003** | E_5001/E_5005/E_5006 ErrorCode 語意錯亂 | 從 Final Approval 識別 | Sprint 17+ 規劃 |
| **TD-004** | E_8000 ErrorCode 過於通用 | 從 Final Approval 識別 | Sprint 17+ 規劃 |
| **TD-005** | Review entity sellerReply 欄位殘留 | 從 Final Approval 識別 (Sprint 17+ 重構需 migration) | Sprint 17+ 規劃 |

### 3.4 文件面

| 問題 | 影響 | 建議 | 優先級 |
|------|------|------|--------|
| **Sprint 16 Review 草稿未在中期產出** | Retro 與 Final Approval 同時撰寫,容易遺漏 | Sprint 中期先寫初版 Review 草稿 | P2 |
| **TC_M08_Review 案例 28 個** | 涵蓋多圖評價但需持續維護 | Sprint 17 視需要新增 | P3 |

---

## 4. 行動項目 (Action Items)

### 4.1 Sprint 17 必須實現 (從本次 Sprint 識別的 P0 問題)

| ID | 行動項目 | 負責人 | 優先級 | 預估 SP | 狀態 |
|----|----------|--------|--------|---------|------|
| **AI-101** | 🔴 **修復 83 個既有測試 bug** (M07PaymentMockIntegrationTest doNothing, OrderService.updateOrderStatus 簽名, 等) | Dev/QA | **P0** | 3 | 待實現 |
| **AI-102** | ~~🔴 Sprint 15 Release 補做合併 (合併 release/v2026.06.04-01 → main + tag)~~ | Dev | ~~P0~~ | ~~1~~ | **✅ 已完成 (2026-06-06 確認 PR #13+#14 已 MERGED)** |
| **AI-103** | 🔴 **Sprint 16 Release 不能再次跳過** (建立 release/v2026.06.06-01 → main + tag) | Dev | **P0** | 0.5 | 待執行 |
| **AI-104** | 🔴 **Final Approval 流程改進**: 必須跑完整 mvn test (而非只看新增測試) | PM/PO + QA | **P0** | 0.5 | 待建立流程 |
| **AI-105** | **CMS MediaService 拆分** (299 行 → MediaUploadService + MediaValidationService) | Dev | P1 | 2 | 評估中 |

### 4.2 架構改進 (Sprint 17 評估)

| ID | 行動項目 | 負責人 | 優先級 | 預估 SP | 狀態 |
|----|----------|--------|--------|---------|------|
| **TI-101** | 評估正式啟用 Flyway + 同步 V13/V22 schema | SD | P1 | 1 | 評估中 |
| **TI-102** | cms.MediaService 內部 StorageService 抽象化 | SD | P2 | 1 | 評估中 |
| **TI-103** | ErrorCode 重構 (E_5001/5005/5006 + E_8000) | Dev | P2 | 1 | 評估中 |

### 4.3 文件改進 (Sprint 17+)

| ID | 行動項目 | 負責人 | 優先級 | 狀態 |
|----|----------|--------|--------|------|
| **DI-101** | 更新 `SPRINT_16_FINAL_APPROVAL.md` 反映完整 mvn test 結果 (489 個測試,非 74 個) | Dev | P0 | 待更新 |
| **DI-102** | 補充 `SPRINT_15_RETRO.md` 連結到本 Retro 的 JPA 衝突發現 | Dev | P2 | 待更新 |
| **DI-103** | 為 `cms.MediaAsset` 欄位擴展 (category/tags/altText/title/isDeleted) 建立 SD 文件說明 | SA | P2 | 待建立 |

### 4.4 流程改進 (Sprint 17 必須建立)

| ID | 行動項目 | 負責人 | 優先級 | 狀態 |
|----|----------|--------|--------|------|
| **PI-101** | 建立「Final Approval 前強制 mvn test 完整跑」檢查點 | PM/PO | P0 | 待建立 |
| **PI-102** | 建立「Commit 前必須 mvn test 通過」檢查點 | Dev | P0 | 待建立 |
| **PI-103** | Release 流程追蹤表 (避免連續 2+ Sprint 跳過 Release) | PM/PO | P0 | 待建立 |

---

## 5. Sprint 16 數據分析

### 5.1 Velocity 分析

| Sprint | 規劃 SP | 完成 SP | Velocity | 主要內容 |
|--------|---------|---------|----------|----------|
| Sprint 13 | 25 SP | 25 SP | 1.0 | M18 Phase 2-B + M08 評價系統 Phase 1 |
| Sprint 14 | 21 SP | 21 SP | 1.0 | M09 MQ + M07 Stripe + M18 FAQ + Refund |
| Sprint 15 | 18 SP | 18 SP | 1.0 | M08 Phase 2 + M07 結算 + 標記 |
| **Sprint 16** | **12 SP** | **12 SP** | **1.0** | **M08 多圖 + 結算強化 + Pre-commit + JPA 修復** |
| **平均** | **76 SP** | **76 SP** | **1.0** | **穩定的高產出** |

### 5.2 Sprint 16 Commit 統計

| 類型 | 數量 | Commit Hash | 說明 |
|------|------|-------------|------|
| Feature | 1 | `70d9310` | M08 商家回覆評價 + 評價標記 (前 Sprint 16 開始) |
| Refactor | 1 | `72bfc70` | M07 SettlementService 拆分為 4 個元件 (TI-002) |
| Test | 2 | `1aed427`, `3d56949` | SettlementService UT + BookingReviewService UT |
| **整合 Commit** | 1 | `c66b8ca` | **Sprint 16 完整發佈 (US-001~US-008) + JPA 衝突修復** |
| **合計** | **5** | | **總計 38 檔案變更,~1,500 行新增** |

### 5.3 程式碼品質指標

| 指標 | 數值 | 評估 |
|------|------|------|
| 平均檔案大小 | ~40 行/檔案 | ✅ 良好 |
| Service 層最大檔案 | cms.MediaService 299 行 | ⚠️ 偏大,建議 Sprint 17 拆分 |
| Controller 層最大檔案 | ReviewController 232 行 | ✅ 合理 |
| Migration 連續性 | V37 (無跳號) | ✅ 良好 |
| Test 更新範圍 | 5 個新測試類別 (UT+IT) | ✅ Sprint 16 涵蓋完整 |
| **完整 mvn test** | 489 個測試,406 通過 (83%) | ⚠️ 83 個既有 bug 為技術債 |

### 5.4 模組完成度更新

| 模組 | Phase 1 | Phase 2-A | Phase 2-B | Phase 2-C | 總體 |
|------|---------|-----------|-----------|-----------|------|
| M08 評價系統 | ✅ Sprint 13 | - | ✅ Sprint 15 | ✅ Sprint 16 (多圖) | 100% |
| M07 金流 | ✅ Sprint 12 | ✅ Sprint 13 | ✅ Sprint 14 | ✅ Sprint 15 + 16 (強化) | Phase 2-C ✅ |
| M09 通知系統 | - | ✅ Sprint 12 | ✅ Sprint 14 | - | 100% |
| M18 知識管理 | ✅ Sprint 12 | ✅ Sprint 13 | ✅ Sprint 14 | - | 100% |
| M15 媒體 (CMS) | ✅ Sprint 8 | ✅ Sprint 16 (整合) | - | - | 100% (含 Review 整合) |

---

## 6. 團隊回饋

### 6.1 Dev (David) 回饋

> "Sprint 16 同時處理 M08 多圖評價 (US-005/006) + SettlementService 拆分重構 (TI-002) + Pre-commit Hook (US-007),工作量飽滿但都在預期內。最重要的發現是:Final Approval 雖然標記測試 100% 通過,但實際完整跑 mvn test 才揭露了 JPA 衝突這個 3 個月未發現的嚴重問題。這教訓提醒我們:Final Approval 必須跑完整測試,不能只看 Sprint 範圍內的測試。"

### 6.2 QA (Quincy) 回饋

> "Sprint 16 新增 29 個測試全部通過 (ReviewReply 10 + Settlement 25 + M07 IT 8 + M08 IT 5 + Job IT 6),這是品質的保證。但揭露的 83 個既有測試 bug (M07PaymentMockIntegrationTest 等) 是長期未被發現的,主要是 Mock 對非 void 方法用 doNothing() 這類低級錯誤。建議 Sprint 17 安排專門的測試 bug 修復衝刺。"

### 6.3 PM/PO (Victoria) 回饋

> "Sprint 16 雖然規模 12 SP 較小 (相對 Sprint 15 的 18 SP),但完成度高 (100%)。JPA 衝突修復的發現是一個重要轉捩點:這顯示我們的 Final Approval 流程需要改進,不能再只關注 Sprint 範圍內的測試。Sprint 17 必須處理:1) Sprint 16 Release,2) 83 個測試 bug,3) Final Approval 流程改進。"

### 6.4 SD (Marcus) 回饋

> "SettlementService 拆分為 4 個元件 (Calculator/Generator/Reviewer/Service) 是一個好的架構演進。JPA 衝突的根因是 `media.MediaAsset` 跟 `cms.MediaAsset` 兩個 Entity 對應同一 table,這是 Sprint 10 引入的技術債。修復採用『保留 cms,刪除 media』是對的方向,但需要持續關注 cms.MediaAsset 的欄位擴展是否合理。建議 Sprint 17 評估:1) cms.MediaService 進一步拆分 (299 行),2) Flyway 正式啟用,3) ErrorCode 重構。"

### 6.5 Architect 觀點 (Claude Code)

> "Sprint 16 的架構演進合理 (SettlementService 拆分 + ReviewReply 1:1 抽出 + 多圖評價 API),但 Final Approval 流程暴露了嚴重的測試覆蓋盲點。本次 JPA 衝突的修復採用『保留 cms,擴展其 Entity 欄位』是務實的決定,但長期應考慮完全重構 (例如合併 media 跟 cms 模組)。"

---

## 7. 經驗教訓 (Lessons Learned)

### 7.1 流程面

1. **🔴 Final Approval 測試必須完整跑**:本次 Sprint 16 的慘痛教訓。Final Approval 只跑了 Sprint 16 新增的 74 個測試,但實際專案有 489 個測試,這導致 JPA 衝突在 Final Approval 之後才被發現。**Sprint 17 起必須強制完整 mvn test**。
2. **🔴 Release 不能連續跳過**:Sprint 15 Release 已合併 (PR #13+#14),但 Sprint 16 Release 又有跳過風險。Release 是 AISDLC 標準流程,跳過會造成技術債累積。
3. **Sprint 容量調整的價值**:Sprint 16 從 18 SP 降為 12 SP 是合理的,給予 buffer 處理 Release 補做。但實際上 Sprint 16 Day 1 沒有執行 Sprint 15 Release 補做(後由 Sprint 15 團隊於 2026-06-04 完成合併,跟 Sprint 16 無關)。
4. **Pre-commit Hook 立即見效**:Sprint 16 US-007 加上 Pre-commit Hook 後,後續 commit 都自動通過 ESLint 與 tsc 檢查,有效預防 Sprint 15 的 2 個 CI 修復重演。

### 7.2 技術面

1. **🔴 JPA Entity 衝突是定時炸彈**:`media.MediaAsset` 跟 `cms.MediaAsset` 對應同一 `media_assets` table 從 Sprint 10 開始埋下,Sprint 16 才被揭露。**未來必須強制檢查:同一個 @Table 不能有多個 @Entity**。
2. **Flyway 停用的代價**:`spring.flyway.enabled: false` 讓 V13/V22 從未執行,Schema 靠 Hibernate 自動管理。雖然運作正常,但失去 Migration 追蹤能力。Sprint 17 應評估正式啟用 Flyway。
3. **拆分大型 Service 的價值**:SettlementService 從 331 行拆分為 4 個專責元件後,各個職責清晰,可獨立測試。這是 Sprint 16 TI-002 的成功實踐。
4. **既有測試 bug 的隱蔽性**:83 個測試 bug (M07PaymentMockIntegrationTest 等) 長期未被發現,是因為 Final Approval 只看 Sprint 範圍測試。**完整 mvn test 是唯一可靠手段**。

### 7.3 團隊面

1. **2 人團隊的極限**:本次 Sprint 16 12 SP 對 2 人團隊適中,但加上 JPA 衝突修復 (~1 小時) 後接近極限。Sprint 17 處理 83 個測試 bug 需考慮工作量分配。
2. **AI 協作的價值**:Claude Code (Sonnet 4.6) 在 JPA 衝突修復中快速定位根因、規劃修復方案、執行驗證,大幅縮短 debug 時間。
3. **文件驅動的紀律性**:本次 Sprint 16 嚴格遵循 Plan → Tasks → Code Review → Final Approval → Retrospective 的順序,雖然發現 Final Approval 流程不完善,但其他流程都符合 AISDLC 標準。

---

## 8. 下一步建議

### 8.1 立即行動（合併 Release 之前 - 阻斷性）

1. **🔴 建立 Sprint 16 Release 分支**:`release/v2026.06.06-01`,從 develop 切出,跑 CI
2. **🔴 合併 Sprint 16 Release 分支**:將 `release/v2026.06.06-01` 合併至 `main` (PR #15)
3. **🔴 建立 Sprint 16 Git Tag**:`v2026.06.06-01`
4. ~~Sprint 15 Release 補做 (PR #13+#14 已 MERGED, 2026-06-06 確認)~~ ✅

### 8.2 Sprint 17 準備 (基於 Action Items)

1. **優先實現**:AI-101 (83 個測試 bug 修復) + AI-103 (Sprint 16 Release 流程) + AI-104 (Final Approval 流程改進)
2. **架構改進**:TI-101 (Flyway 評估) + AI-105 (cms.MediaService 拆分)
3. **文件補完**:DI-101 (SPRINT_16_FINAL_APPROVAL.md 更新) + DI-103 (cms.MediaAsset 欄位文件)
4. **流程改進**:PI-101/102/103 (檢查點建立)

### 8.3 長期改進 (Sprint 18+)

1. **cms.MediaService 拆分**:299 行偏大,建議拆分為 MediaUploadService + MediaValidationService
2. **完全合併 media 跟 cms 模組**:長期應考慮合併兩個模組的 MediaService 與相關 Entity
3. **ErrorCode 重構**:E_5001/E_5005/E_5006 與 E_8000 語意重整
4. **Review entity 清理**:移除 sellerReply 欄位 (Sprint 15 Sprint 16 殘留)
5. **自動化文件**:Swagger/OpenAPI 自動生成 API Spec

---

## 9. 確認簽核

| 角色 | 確認狀態 | 簽核日期 | 備註 |
|------|----------|----------|------|
| Human User | ⏳ 待確認 | - | - |
| PM/PO (Victoria) | ⏳ 待確認 | - | - |
| SA (Amanda) | ⏳ 待確認 | - | - |
| SD (Marcus) | ⏳ 待確認 | - | - |
| Dev (David) | ⏳ 待確認 | - | - |
| QA (Quincy) | ⏳ 待確認 | - | - |
| Architect | ⏳ 待確認 | - | - |

---

**文件版本**: v1.0
**最後更新**: 2026-06-06
**基於 AISDLC**: v0.09

## 📝 文件修訂紀錄

| 版本 | 日期 | 作者 | 變更內容 |
|------|------|------|----------|
| v1.0 | 2026-06-06 | Claude Code (Sonnet 4.6) | 初版建立,Sprint 16 Retrospective 完整報告 |
