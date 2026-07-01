# Release 流程追蹤表 / Release Tracker

> **文件類型**: 追蹤表 (Tracker)
> **版本**: v1.0
> **建立日期**: 2026-06-11
> **目的**: 追蹤每個 Sprint 的 Release 狀態，避免連續多個 Sprint 跳過 Release

---

## 📋 Release 總覽

| Sprint | Release Tag | PR 號碼 | 合併日期 | 主要功能 | 狀態 |
|--------|-------------|---------|----------|----------|------|
| Sprint 31 | v2027.01.02-01 | - | 2026-07-01 | 買家閉環後端驗證(BuyerJourney E2E) + roomTitle 填充(AI-1502) + DEF-016 audit 持久化(V57)；揪出 getOrder IDOR(DEF-018) | ⏳ 待 push（與 S29+30 累積批次） |
| Sprint 30 | v2026.12.19-01 | - | 2026-07-01 | EPIC-BUYER 買家端閉環完成(純前端)：M06 預訂管理 + M08 評價(提交/列表) + M11 物流追蹤(訂單詳情) | ⏳ 待 push（與 Sprint 29 累積批次） |
| Sprint 29 | v2026.12.05-01 | - | 2026-07-01 | EPIC-BUYER 買家端閉環起手(純前端)：M05 訂單前端(列表/詳情/取消/狀態日誌) + M09 通知收件匣 + M07 Mock 付款(訂單詳情整合) | ⏳ 待 push（累積 US-001/002/003，檢查點徵詢後完整守門） |
| Sprint 28 | v2026.11.21-01 | - | 2026-07-01 | 品質硬化(M14/M18 測試 0→13) + 商家營運總覽儀表板(營收/訂單/趨勢) + US-004 調查(ERP 租戶隔離 no-op→DEF-017、audit→DEF-016) | ✅ 已 push（本地優先驗證） |
| Sprint 27 | v2026.11.07-01 | - | 2026-07-01 | DEF-013 通知端到端斷鏈修復 + DEF-015 前端離線 build + pre-push v5 完整守門實證 + 產品方向決策(PRODUCT_BACKLOG) | ✅ 已 push（本地優先驗證；活躍 DEF 歸零） |
| Sprint 26 | v2026.10.24-01 | - | 2026-07-01 | 本地優先 CI（停用雲端自動 CI）+ WS/即時 DoD 制度化 + M11 取消技術債清償(DEF-010/011) + 廣播 conversationId(DEF-012) + Logistics jsonb 統一(DEF-009) + e2e strict 守門 | ✅ 已 push（本地優先驗證；雲端改手動觸發） |
| Sprint 25 | v2026.10.10-01 | - | 2026-06-30 | schema 漂移守門關卡 + Conversation tenant_id(V56) + M10 WebSocket 前端整合(live E2E) + SSH keepalive + M11 取消規則確認 | ✅ 已 push（本地優先驗證；雲端改手動觸發） |
| Sprint 24 | v2026.09.26-01 | - | 2026-06-29 | M10 WebSocket STOMP 即時訊息 + M13 Redis TTL + 整合測試標準化 + E2E schema 修復(V48~V55) | ✅ |
| Sprint 23 | v2026.09.12-01 | - | 2026-06-27 | M10 IM Migration(V45/V46) + M11 物流履約整合 + 運費接入 + M13 @Cacheable | ✅ |
| Sprint 22 | v2026.08.29-01 | - | 2026-06-27 | 詳見 RELEASE_NOTES_v2026.08.29-01.md | ✅ |
| Sprint 21 | v2026.08.15-01 | - | 2026-06-27 | MQ 一致性 + Stripe Phase 3 + M11 Provider + M14 統計 + 運費模板 | ✅ |
| Sprint 20 | v2026.08.01-01 | - | 2026-06-26 | MQ 技術債清零 + @Deprecated 清零 + M09 通知歷史 + M08 評分統計 | ✅ |
| Sprint 19 | v2026.07.18-01 | - | 2026-06-25 | 詳見 RELEASE_NOTES_v2026.07.18-01.md | ✅ |
| Sprint 18 | v2026.07.03-01 | - | 2026-06-24 | 詳見 RELEASE_NOTES_v2026.07.03-01.md | ✅ |
| Sprint 17 | v2026.06.19-01 | #17 | 2026-06-10 | US-004/005 完成 - Flyway 啟用 + sellerReply 清理 | ✅ |
| Sprint 16 | v2026.06.06-01 | #15 | 2026-06-06 | M08 評價多圖 + M07 結算強化 + Pre-commit | ✅ |
| Sprint 15 | v2026.06.04-01 | #13, #14 | 2026-06-04 | M08 商家回覆 + 評價標記 | ✅ |
| Sprint 14 | v2026.05.16-02 | #12 | 2026-05-16 | M09 MQ 通知 + M07 Stripe 整合 | ✅ |
| Sprint 13 | v2026.05.16-01 | #11 | 2026-05-16 | M18 知識庫版本控制 + 排程發布 | ✅ |
| Sprint 12 | v2026.05.15-01 | #10 | 2026-05-15 | M18 知識管理 + M07 金流準備 + M09 通知模板 | ✅ |
| Sprint 11 | v2026.05.12-01 | #7 | 2026-05-12 | CI/CD Pipeline 修復 | ✅ |
| Sprint 10 | v2026.05.09-01 | #5 | 2026-05-09 | M16 ERP Backend | ✅ |
| Sprint 9 | - | - | - | M15 CMS Backend | ⚠️ 未正式 Release |
| Sprint 8 | - | - | - | M15 CMS Backend | ⚠️ 未正式 Release |
| Sprint 1-7 | - | - | - | 初期開發階段 | ⚠️ 無記錄 |

---

## 📊 Release 統計

| 項目 | 數值 |
|------|------|
| 正式 Release 次數 | 23 (Sprint 10-31) |
| 跳過 Release 次數 | 2 (Sprint 8-9) |
| 最近一次 Release | v2027.01.02-01 (Sprint 31，待 push) |
| 最近一次跳過 | Sprint 8-9 |
| 連續 Release 開始 | Sprint 10 |

---

## ⏳ Sprint 31 Release（最新）

| 欄位 | 內容 |
|------|------|
| **Tag** | v2027.01.02-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | 買家閉環後端驗證（BuyerOrderJourneyE2ETest）+ BookingListResponse roomTitle 填充（AI-1502）+ DEF-016 Admin audit log 持久化（AuditLog + V57）；US-002 揪出 getOrder IDOR（DEF-018） |
| **測試狀態** | 後端 `@Test` 689（+6）, catch(Exception)=0, @Deprecated=0, make validate-schema 無漂移, 活躍 DEF=2（DEF-017/018） |
| **Flyway** | **V57**（audit_log） |
| **Release Notes** | [RELEASE_NOTES_v2027.01.02-01.md](../08_deployment/RELEASE_NOTES_v2027.01.02-01.md) |
| **狀態** | ⏳ 待 push（與 S29+30 累積批次，完整守門一次驗證） |

---

## ⏳ Sprint 30 Release

| 欄位 | 內容 |
|------|------|
| **Tag** | v2026.12.19-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | EPIC-BUYER 買家端閉環**完成**（純前端）：M06 預訂管理（我的預訂/詳情/取消）+ M08 評價（提交/列表/評分統計）+ M11 物流追蹤（訂單詳情軌跡時間軸） |
| **測試狀態** | 後端 `@Test` 683（純前端無變化）, 前端 lint 0 errors/type-check/build 通過, catch(Exception)=0, @Deprecated=0, 活躍 DEF=2 |
| **Flyway** | V56（無新 migration；後端零變更） |
| **Release Notes** | [RELEASE_NOTES_v2026.12.19-01.md](../08_deployment/RELEASE_NOTES_v2026.12.19-01.md) |
| **狀態** | ⏳ 待 push（與 Sprint 29 累積批次，完整守門一次驗證） |

---

## ⏳ Sprint 29 Release

| 欄位 | 內容 |
|------|------|
| **Tag** | v2026.12.05-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | EPIC-BUYER 買家端閉環起手（純前端）：M05 訂單前端（列表/詳情/取消/狀態日誌）+ M09 通知收件匣（未讀/篩選/已讀/刪除）+ M07 Mock 付款（訂單詳情整合，CREATED→PAID） |
| **測試狀態** | 後端 `@Test` 683（純前端無變化）, 前端 lint 0 errors/type-check/build 通過, catch(Exception)=0, @Deprecated=0, 活躍 DEF=2 |
| **Flyway** | V56（無新 migration；後端零變更） |
| **Release Notes** | [RELEASE_NOTES_v2026.12.05-01.md](../08_deployment/RELEASE_NOTES_v2026.12.05-01.md) |
| **狀態** | ⏳ 待 push（與 Sprint 30 累積批次） |

---

## ✅ Sprint 28 Release

| 欄位 | 內容 |
|------|------|
| **Tag** | v2026.11.21-01 |
| **建立日期** | 2026-07-01 |
| **主要功能** | 品質硬化（M14 Analytics/M18 FAQ 測試 0→13）+ 商家營運總覽儀表板（營收/訂單/趨勢）+ US-004 調查（ERP 租戶隔離 no-op→DEF-017、audit→DEF-016） |
| **測試狀態** | `@Test` 靜態計數 683（+13）, catch(Exception)=0, @Deprecated=0, 活躍 DEF=2 |
| **Flyway** | V56（無新 migration） |
| **Release Notes** | [RELEASE_NOTES_v2026.11.21-01.md](../08_deployment/RELEASE_NOTES_v2026.11.21-01.md) |
| **狀態** | ✅ 完成 |

---

## 🔴 Sprint 22 Release 規劃

**目標**: Sprint 22 結束（2026-08-29）執行 Release，Tag = `v2026.08.29-01`

### Release 前檢查清單

| 檢查項目 | 標準 | 狀態 |
|---------|------|------|
| 所有 US 完成 | AC 100% 達成 | ⏳ |
| mvn verify 100% 通過 | 0 Failures, 0 Errors | ⏳ |
| Checkstyle | 0 violations | ⏳ |
| Sprint 22 Review 文件 | SPRINT_22_REVIEW.md 建立 | ⏳ |
| Sprint 22 Retro 文件 | SPRINT_22_RETRO.md 建立 | ⏳ |
| RELEASE_TRACKER.md 更新 | 新增 Sprint 22 記錄 | ⏳ |

---

## 📝 Release 歷史詳細資料

### Sprint 16 (v2026.06.06-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #15 |
| **合併日期** | 2026-06-06 |
| **主要功能** | M08 評價多圖 (9張上限) + M07 結算強化 + Pre-commit Hook |
| **架構異動** | JPA 衝突修復 (media.MediaAsset vs cms.MediaAsset) |
| **技術債** | 83 個既有測試 bug (需 Sprint 17 修復) |
| **測試覆蓋** | 新增 34 個測試，100% 通過 |

### Sprint 17 (v2026.06.19-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #17 |
| **合併日期** | 2026-06-10 |
| **主要功能** | US-004/005 完成 - Flyway 啟用 + sellerReply 清理 + 83個測試 bug 修復 |
| **架構異動** | V38/V39 Migration 建立、sellerReply 欄位移除 |
| **技術債清理** | 83個測試 bug 歸零、已棄用方法移除 |
| **流程改進** | CI/CD Pipeline 優化、Artifact 配額管理改善 |
| **測試覆蓋** | 532 tests, 0 Failures, 0 Errors (100%) |
| **Release** | ✅ 已建立 (v2026.06.19-01) |

### Sprint 15 (v2026.06.04-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #13, #14 (CI hotfix) |
| **合併日期** | 2026-06-04 |
| **主要功能** | M08 商家回覆評價 + 評價標記功能 |
| **重要變更** | ReviewReply Entity 建立 (1:1 with Review) |
| **取消功能** | sellerReply 欄位廢除 (改用 ReviewReply) |

### Sprint 14 (v2026.05.16-02)

| 欄位 | 內容 |
|------|------|
| **PR** | #12 |
| **合併日期** | 2026-05-16 |
| **主要功能** | M09 MQ 通知 + M07 Stripe 整合 + M18 FAQ 進階 |

### Sprint 13 (v2026.05.16-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #11 |
| **合併日期** | 2026-05-16 |
| **主要功能** | M18 知識庫版本控制 + 排程發布 + M08 預訂評價系統 |

### Sprint 12 (v2026.05.15-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #10 |
| **合併日期** | 2026-05-15 |
| **主要功能** | M18 知識管理 + M07 金流準備 + M09 通知模板 |

### Sprint 11 (v2026.05.12-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #7 |
| **合併日期** | 2026-05-12 |
| **主要功能** | CI/CD Pipeline 修復 |

### Sprint 10 (v2026.05.09-01)

| 欄位 | 內容 |
|------|------|
| **PR** | #5 |
| **合併日期** | 2026-05-09 |
| **主要功能** | M16 ERP Backend 完成 |

---

## ⚠️ 未 Release 的 Sprint

### Sprint 8-9 問題說明

> **歷史問題**: Sprint 8 和 Sprint 9 沒有執行正式的 Release 流程，導致：
> - M15 CMS Backend 功能未能及時進入 Production
> - 程式碼累積在 develop 分支
> - 技術債逐漸累積

### 補救措施

1. ✅ Sprint 10 時已將 M15 CMS 程式碼带入 main
2. ✅ 後續 Sprint 都有執行 Release 流程
3. ⚠️ 建議建立文件記錄 Sprint 8-9 的功能事實上已進入 Production

---

## 📈 Release 頻率趨勢

```
Sprint 10  → ✅ Release (v2026.05.09-01)
Sprint 11  → ✅ Release (v2026.05.12-01)
Sprint 12  → ✅ Release (v2026.05.15-01)
Sprint 13  → ✅ Release (v2026.05.16-01)
Sprint 14  → ✅ Release (v2026.05.16-02)
Sprint 15  → ✅ Release (v2026.06.04-01)
Sprint 16  → ✅ Release (v2026.06.06-01)
Sprint 17  → ✅ Release (v2026.06.19-01)
Sprint 18  → ✅ Release (v2026.07.03-01)
Sprint 19  → ✅ Release (v2026.07.18-01)
Sprint 20  → ✅ Release (v2026.08.01-01)
Sprint 21  → ✅ Release (v2026.08.15-01)
Sprint 22  → ✅ Release (v2026.08.29-01)
Sprint 23  → ✅ Release (v2026.09.12-01)
Sprint 24  → ✅ Release (v2026.09.26-01)
Sprint 25  → ✅ Release (v2026.10.10-01)
Sprint 26  → ✅ Release (v2026.10.24-01)
Sprint 27  → ✅ Release (v2026.11.07-01)
Sprint 28  → ✅ Release (v2026.11.21-01)
```

**連續 Release**: 20 次 (Sprint 10-28)

---

## 🔧 使用方式

### 在 Sprint Planning 時

1. 開啟此文件
2. 確認上一個 Sprint 的 Release 狀態
3. 將 Release 追蹤加入 Sprint Planning Template 檢查清單

### 在 Final Approval 時

1. 確認 Release Tag 已建立
2. 確認 PR 已合併至 main
3. 更新此文件的 Release 狀態
4. 建立 GitHub Release (如尚未建立)

### 在 Sprint Retrospective 時

1. 檢視 Release 頻率
2. 識別任何跳過的 Release
3. 討論改善措施

---

## 📚 相關文件

| 文件 | 路徑 |
|------|------|
| Sprint 17 Plan | `docs/04_planning/SPRINT_17_PLAN.md` |
| Sprint 17 Tasks | `docs/05_development/SPRINT_17_TASKS.md` |
| Final Approval Process | `docs/04_planning/SPRINT_FINAL_APPROVAL_PROCESS.md` |
| Execution Checklist | `docs/04_planning/EXECUTION_CHECKLIST.md` |

---

## 📝 歷史版本

| 版本 | 日期 | 修改內容 |
|------|------|----------|
| v1.0 | 2026-06-11 | 初始建立，包含 Sprint 10-16 Release 歷史資料 |

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-11
**作者**: Claude Code (AI Assistant)
**維護責任**: PM/PO (每個 Sprint 結束後更新)