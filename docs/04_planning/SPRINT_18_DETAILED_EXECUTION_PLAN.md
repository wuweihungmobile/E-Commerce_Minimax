# Sprint 18 詳細執行計劃 / Sprint 18 Detailed Execution Plan

> **Sprint 編號**: Sprint 18
> **期間**: 2026-06-22 ~ 2026-07-03 (2 週，10 個工作天)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-06-22
> **作者**: Claude Code (Architect)
> **基於**: [SPRINT_18_PLAN.md](./SPRINT_18_PLAN.md) + [SPRINT_17_RETRO.md](../05_development/SPRINT_17_RETRO.md) + [FLYWAY_EVALUATION.md](./FLYWAY_EVALUATION.md)

---

## 🔴 重要提醒

> **本文件為 Sprint 18 詳細執行指南，補充 SPRINT_18_PLAN.md 的「如何執行」細節。**
> **本文件不重複定義 US 內容，僅提供：每日時程、執行步驟、風險緩解、檢查點、決策樹。**

---

## 1. 每日執行時程表 (Daily Schedule)

### Day-by-Day Plan

| Day | 日期 | 主要任務 | 對應 US | 預估工時 | 檢查點 |
|-----|------|----------|---------|----------|--------|
| **Day 1 (週一)** | 06-22 | 環境驗證 + Flyway 啟用準備 | US-001 開工 | 4h | ✅ mvn test 基準線 |
| **Day 2 (週二)** | 06-23 | V38 Migration 建立 + 驗證 | US-001 T-001-3 | 6h | ✅ V38 編譯通過 |
| **Day 3 (週三)** | 06-24 | Flyway 啟用 + 532 測試驗證 | US-001 T-001-1~2,5 | 8h | ✅ 532 tests 100% 通過 |
| **Day 4 (週四)** | 06-25 | Flyway 文件收尾 + M08 需求確認 | US-001 收尾 + US-003 開工 | 4h+4h | ✅ 文件 + 需求確認 |
| **Day 5 (週五)** | 06-26 | M08 新功能實作 | US-003 | 8h | ✅ 程式碼完成 |
| **Day 6 (週一)** | 06-29 | M08 測試 + 前端 type-check | US-003 + US-002 | 4h+4h | ✅ type-check 啟用 |
| **Day 7 (週二)** | 06-30 | M08 測試收尾 + ErrorCode 評估 | US-003 + US-004 | 4h+4h | ✅ 評估文件 |
| **Day 8 (週三)** | 07-01 | ErrorCode 評估 + Buffer | US-004 + US-005 | 4h+4h | ✅ 評估完成 |
| **Day 9 (週四)** | 07-02 | Sprint 18 Review | 文件產出 | 6h | ✅ Review 文件 |
| **Day 10 (週五)** | 07-03 | **Sprint 18 Release (不可跳過)** | Release 流程 | 4h | ✅ Tag + PR 合併 |

---

## 2. 各 User Story 詳細執行步驟

### 2.1 US-001: 正式啟用 Flyway (5 SP) - P0 最關鍵

#### 執行順序（嚴格遵守）

```
Step 1: 環境基準驗證
   ↓
Step 2: 建立 V38 Migration
   ↓
Step 3: 驗證 V38 等冪性
   ↓
Step 4: 修改 application.yml
   ↓
Step 5: 停用 Hibernate ddl-auto
   ↓
Step 6: 執行 mvn test (532 個)
   ↓
Step 7: 更新文件
```

#### Step 1: 環境基準驗證 (Day 1 上午)

**目的**: 確保 Sprint 17 的 532 個測試基底仍然穩定

**執行命令**:
```bash
# 1. 確認在 main 分支
git branch --show-current  # 預期: main

# 2. 確認 working tree 乾淨
git status  # 預期: clean

# 3. 執行完整 mvn test
cd backend
./mvnw test 2>&1 | tee /tmp/sprint18-baseline-test.log

# 4. 驗證結果
grep -E "Tests run:" /tmp/sprint18-baseline-test.log | tail -3
# 預期: Tests run: 532, Failures: 0, Errors: 0
```

**🔴 檢查點**: 若 baseline 失敗，**立即停止 US-001**，先修復測試基底

---

#### Step 2: 建立 V38__Consolidate_Media_Assets Migration (Day 2)

**位置**: `backend/src/main/resources/db/migration/V38__Consolidate_Media_Assets.sql`

**設計原則**:
- ✅ 結合 V13 和 V22 的 `media_assets` 欄位
- ✅ 等冪性設計（可重複執行）
- ✅ 保護既有資料
- ✅ 配合 FLYWAY_EVALUATION.md 方案 A

**V38 內容指引**:
```sql
-- V38__Consolidate_Media_Assets.sql
-- 目的: 統一 V13 (cms.MediaAsset) 與 V22 (m15_media_assets) 的 schema
-- 依據: FLYWAY_EVALUATION.md 方案 A

-- 1. 檢查當前狀態 (等冪性保護)
DO $$
BEGIN
    -- 若 V22 表已存在於 V13 之前，需重新命名
    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_name = 'm15_media_assets')
       AND NOT EXISTS (SELECT 1 FROM information_schema.tables
                      WHERE table_name = 'media_assets')
    THEN
        ALTER TABLE m15_media_assets RENAME TO media_assets;
    END IF;
END $$;

-- 2. 補齊缺少的欄位
ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS tenant_id UUID,
    ADD COLUMN IF NOT EXISTS entity_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS entity_id UUID,
    ADD COLUMN IF NOT EXISTS variant VARCHAR(50),
    ADD COLUMN IF NOT EXISTS cdn_url TEXT,
    ADD COLUMN IF NOT EXISTS is_primary BOOLEAN DEFAULT FALSE;

-- 3. 建立索引
CREATE INDEX IF NOT EXISTS idx_media_assets_tenant
    ON media_assets(tenant_id);
CREATE INDEX IF NOT EXISTS idx_media_assets_entity
    ON media_assets(entity_type, entity_id);
```

**🔴 強制檢查**: 寫入 migration 前，必須閱讀既有的 V13 和 V22 migration 檔案，確保欄位定義一致

---

#### Step 3: 驗證 V38 等冪性 (Day 2)

**驗證方法**:
```bash
# 1. 第一次執行
./mvnw spring-boot:run &
sleep 30
psql -U koala -d nextkeytest -c "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;"

# 2. 停止服務後，第二次啟動（驗證等冪性）
kill %1
./mvnw spring-boot:run &
sleep 30
psql -U koala -d nextkeytest -c "SELECT version, success FROM flyway_schema_history WHERE version = '38';"
# 預期: success = true (兩次都成功)
```

**🔴 等冪性失敗時**: 立即停止，修正 migration 邏輯

---

#### Step 4-5: 修改 application.yml (Day 3 上午)

**修改項目**:
```yaml
# backend/src/main/resources/application.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # 從 update 改為 validate (Step 5)
  flyway:
    enabled: true         # 從 false 改為 true (Step 4)
    locations: classpath:db/migration
    baseline-on-migrate: false
```

**🔴 注意事項**:
- 兩個修改必須**一起提交**（同一個 commit）
- 修改後立即編譯，確認無誤

---

#### Step 6: 執行 mvn test (Day 3 下午) - 開發-編譯-測試循環

**嚴格遵守 CLAUDE.md 規則**:
```
Step 4-5 修改 application.yml
    ↓
立即編譯
    ↓
編譯失敗？ → 🔴 立即停止 → 修復 → 重新編譯
    ↓
編譯成功 ✅
    ↓
執行 mvn test
    ↓
測試失敗？ → 🔴 立即停止 → 修復 → 重新測試
    ↓
測試通過 ✅
    ↓
繼續
```

**執行命令**:
```bash
# 編譯
./mvnw clean compile

# 完整測試
./mvnw test 2>&1 | tee /tmp/sprint18-flyway-test.log

# 驗證
grep -E "Tests run:" /tmp/sprint18-flyway-test.log | tail -3
# 預期: Tests run: 532, Failures: 0, Errors: 0
```

**🔴 若測試失敗**:
1. 立即停止
2. 取得錯誤訊息: `grep -A 20 "FAIL" /tmp/sprint18-flyway-test.log`
3. 修復（不要「先跳過」）
4. 重新測試
5. **絕不** 修改測試程式碼來讓測試通過

---

#### Step 7: 更新 FLYWAY_EVALUATION.md (Day 3 結束或 Day 4 上午)

**更新內容**:
```markdown
# 在 FLYWAY_EVALUATION.md 開頭加入

## 啟用狀態
- **狀態**: ✅ 已於 Sprint 18 (2026-06-24) 正式啟用
- **方案**: 方案 A - V38 統一 Media Assets Schema
- **測試結果**: 532 tests, 0 Failures, 0 Errors
- **變更檔案**:
  - `backend/src/main/resources/application.yml`
  - `backend/src/main/resources/db/migration/V38__Consolidate_Media_Assets.sql`
```

---

### 2.2 US-002: 前端 Pre-commit type-check (2 SP) - Day 6

**位置**: `frontend/.husky/pre-commit`

**修改內容**:
```bash
#!/usr/bin/env sh
. "$(dirname -- "$0")/_/husky.sh"

# 既有檢查
npx lint-staged

# 新增 type-check (US-002)
cd frontend && npm run type-check
```

**驗證方法**:
```bash
# 故意製造 type-check 錯誤
echo 'const x: number = "string";' > frontend/test-typecheck.ts

# 嘗試 commit (應被阻擋)
git add frontend/test-typecheck.ts
git commit -m "test: type-check 驗證"
# 預期: 失敗訊息 "Type check failed"

# 清理
rm frontend/test-typecheck.ts
```

---

### 2.3 US-003: M08 新功能開發 (3 SP) - Day 4-7

**前置條件**:
- ✅ US-001 Flyway 啟用完成且測試通過
- ✅ PM/PO 確認 M08 下一階段需求

**建議範圍** (依 Sprint 17 Retro 建議):
| 候選功能 | 複雜度 | 預估 SP | 優先級 |
|----------|--------|---------|--------|
| 評價統計強化 (Dashboard) | 中 | 2 | 推薦 |
| 評價標籤自動分類 | 中 | 2 | 推薦 |
| 商家回覆通知 | 低 | 1 | 備選 |

**執行步驟**:
1. Day 4 下午: PM/PO 確認具體功能
2. Day 5: 實作 Service 層 + Controller 層
3. Day 6 上午: 單元測試 (目標 80% 覆蓋率)
4. Day 6 下午: 整合測試
5. Day 7 上午: 完整 mvn test 驗證 (532 + 新增測試)

**🔴 強制規範**:
- 新功能必須包含測試（測試失效的功能不可接受）
- 既有 532 個測試必須 100% 通過
- 遵循「開發 1 支 → 編譯 → 測試 → 下一支」循環

---

### 2.4 US-004: ErrorCode 重構評估 (1 SP) - Day 7-8

**評估文件位置**: `docs/06_quality/ErrorCode_Refactor_Evaluation.md`

**評估內容**:
| ErrorCode | 當前語意 | 問題 | 建議 |
|-----------|----------|------|------|
| E_5001 | 通用錯誤 | 過於通用 | 拆分為更具體錯誤碼 |
| E_5005 | 業務錯誤 | 語意不清 | 重新定義 |
| E_5006 | 系統錯誤 | 與 E_5001 重疊 | 合併或重新定義 |
| E_8000 | 整合錯誤 | 過於通用 | 拆分為 E_8xxx 系列 |

**評估文件結構**:
```markdown
# ErrorCode 重構評估

## 1. 現況分析
- E_5001 使用次數與場景
- E_5005/E_5006 差異
- E_8000 子類型分析

## 2. 影響範圍評估
- 涉及檔案數
- 涉及 API 端點
- 影響前端處理

## 3. 重構方案
- 方案 A: 最小變更 (僅重新命名)
- 方案 B: 中度變更 (拆分為子類型)
- 方案 C: 完整重構 (建立 ErrorCode 體系)

## 4. 建議
- 推薦方案
- 預估工時
- 風險評估
```

---

### 2.5 US-005: 日常開發支援 (1 SP) - Day 8 Buffer

**用途**:
- 緊急 Bug 修復
- 臨時需求處理
- 文件改進
- 技術研究

**觸發條件**:
- 任何 P0/P1 等級的臨時需求
- Sprint 18 Review 識別的立即修正項目

---

## 3. 風險管理與緩解措施

### 3.1 風險矩陣

| 風險 | 機率 | 影響 | 緩解措施 | 負責人 |
|------|------|------|----------|--------|
| **Flyway 啟用導致測試失敗** | 中 | 高 | V38 等冪性設計 + 完整 mvn test 驗證 | Dev |
| **V38 與 V13/V22 欄位衝突** | 中 | 高 | Step 2 詳細欄位比對 + 測試資料驗證 | Dev |
| **M08 需求不明確** | 中 | 中 | Day 4 上午 PM/PO 確認會議 | PM/PO |
| **前端 type-check 影響現有 commit 流程** | 低 | 中 | 先在測試分支驗證 | Dev |
| **Sprint 18 Release 跳過** | 低 | 高 | Day 10 預留時間 + RELEASE_TRACKER.md 提醒 | PM/PO |

### 3.2 風險升級流程

```
風險發生
    ↓
嘗試自行解決 (< 2 小時)
    ↓
無法解決 → 立即升級至 PM/PO
    ↓
PM/PO 決策: 調整範圍 / 增加 Buffer / 延遲至 Sprint 19
    ↓
更新 SPRINT_18_TASKS.md 狀態
```

---

## 4. 檢查點機制 (Checkpoint System)

### 4.1 每日檢查點

**時機**: 每日結束前 30 分鐘

**檢查項目**:
```markdown
## Day X 檢查點 (2026-MM-DD)

### 今日完成
- [x] T-XXX-X 任務
- [x] T-XXX-Y 任務

### 明日計劃
- [ ] T-XXX-Z 任務
- [ ] T-XXX-W 任務

### 風險與阻礙
- 無 / (列出問題)

### mvn test 狀態
- Tests run: XXX, Failures: 0, Errors: 0

### 累計進度
- 規劃 SP: 12 SP
- 已完成 SP: X SP
- 剩餘 SP: Y SP
```

### 4.2 關鍵里程碑檢查點

| 里程碑 | 日期 | 驗收標準 | 失敗處置 |
|--------|------|----------|----------|
| **M1: Flyway 啟用** | Day 3 | 532 tests 100% 通過 | 立即修復，必要時 Revert |
| **M2: M08 程式碼完成** | Day 5 | 程式碼可編譯 | 縮小範圍至 1 SP 功能 |
| **M3: M08 測試完成** | Day 7 | 既有 + 新測試全通過 | 延遲至 Sprint 19 |
| **M4: Release 就緒** | Day 9 | 所有文件產出 | 強制 Day 10 Release |

---

## 5. CI/CD 與本地驗證整合

### 5.1 本地 CI 驗證流程（CLAUDE.md 強制）

**每次 commit 前**:
```bash
# pre-commit hook 自動執行（不可 --no-verify）
git add .
git commit -m "feat(Sprint 18): ..."

# hook 會自動執行:
# 1. checkstyle
# 2. mvn compile
# 3. mvn test
# 4. 整合測試
# 預期: 5-10 分鐘後 commit 成功
```

### 5.2 🔴 禁止行為（CLAUDE.md 強制）

- ❌ **絕不** 使用 `git commit --no-verify`
- ❌ **絕不** 使用 `git push --no-verify`
- ❌ **絕不** 累積多個 commit 後才測試
- ❌ **絕不** 跳過失敗的測試

### 5.3 Push 流程

```bash
# 1. 確認所有 commit 都有 CI 驗證記錄
cat .ci-validation-data/commits | tail -5

# 2. 推送到 origin
git push origin main

# 3. 監控 GitHub Actions
gh run list --limit 5
gh run watch <run-id>
```

---

## 6. 決策樹 (Decision Trees)

### 6.1 測試失敗時的決策

```
mvn test 失敗
    ↓
[Q1] 失敗的是既有 532 個測試？
    ├── YES → 立即停止 US-001，優先修復測試基底
    │         ↓
    │         修復策略:
    │         - 是 V38 migration 導致？→ 修正 V38
    │         - 是 application.yml 導致？→ 修正設定
    │         - 是其他原因？→ 依錯誤訊息修正
    │
    └── NO (新功能測試失敗)
              ↓
              [Q2] 是測試邏輯錯誤還是程式碼錯誤？
              ├── 測試邏輯錯誤 → 修正測試
              └── 程式碼錯誤 → 修正程式碼（不可修改測試讓它通過）
```

### 6.2 時間不足時的決策

```
Day 8 結束時
    ↓
[Q] 還有未完成 US？
    ├── 無 → 正常進行 Day 9-10
    │
    └── 有 US-003 M08 未完成
              ↓
              [Q] M08 完成度？
              ├── ≥ 80% → 繼續完成
              ├── 50-80% → 縮小範圍，先完成核心
              └── < 50% → 延遲至 Sprint 19，立即通知 PM/PO
```

### 6.3 Release 跳過的決策

```
Day 10
    ↓
[Q] 是否有 US 未完成？
    ├── NO → 執行 Release (PR + Tag + GitHub Release)
    │
    └── YES (有 US 未完成)
              ↓
              [Q] 是否有 P0 等級 US 未完成？
              ├── YES → 🔴 **不可跳過 Release**
              │         考慮:
              │         - 縮小 US 範圍
              │         - 延遲 US 至 Sprint 19
              │         - 將未完成 US 記錄於 DEFERRED_ITEMS_TRACKER.md
              │
              └── NO (僅 P1/P2 未完成)
                        ↓
                        評估是否為關鍵功能
                        ├── 關鍵 → 縮小 US 範圍，Release
                        └── 非關鍵 → 延遲至 Sprint 19，Release
```

---

## 7. 每日產出文件清單

### 7.1 必要文件（每日產出）

| Day | 文件 | 位置 | 狀態 |
|-----|------|------|------|
| Day 1 | 環境驗證記錄 | `/tmp/sprint18-baseline-test.log` | ⏳ |
| Day 2 | V38 migration 檔案 | `backend/src/main/resources/db/migration/V38__Consolidate_Media_Assets.sql` | ⏳ |
| Day 3 | 測試報告 | `/tmp/sprint18-flyway-test.log` | ⏳ |
| Day 4 | M08 需求確認記錄 | 更新 `SPRINT_18_TASKS.md` | ⏳ |
| Day 6 | type-check 驗證 | `frontend/.husky/pre-commit` | ⏳ |
| Day 7 | ErrorCode 評估文件 | `docs/06_quality/ErrorCode_Refactor_Evaluation.md` | ⏳ |
| Day 9 | Sprint 18 Review | `docs/05_development/SPRINT_18_REVIEW.md` | ⏳ |
| Day 10 | Sprint 18 Release | `docs/04_planning/RELEASE_TRACKER.md` (更新) | ⏳ |
| Day 10 | Sprint 18 Retro | `docs/05_development/SPRINT_18_RETRO.md` | ⏳ |

### 7.2 既有文件更新

| 文件 | 更新時機 | 更新內容 |
|------|----------|----------|
| `SPRINT_18_TASKS.md` | 每日 | 任務完成狀態 |
| `FLYWAY_EVALUATION.md` | Day 3 | 標註已啟用 |
| `RELEASE_TRACKER.md` | Day 10 | 加入 Sprint 18 記錄 |
| `CHANGELOG.md` | Day 10 | Sprint 18 變更摘要 |

---

## 8. 與既有 Sprint 文件的關係

### 8.1 文件層級

```
SPRINT_18_PLAN.md (高層次: 目標、SP、AC)
    ↓ 補充
SPRINT_18_DETAILED_EXECUTION_PLAN.md (本文件: 每日時程、執行步驟) ← 你在這裡
    ↓ 細化
SPRINT_18_TASKS.md (具體任務清單)
```

### 8.2 文件職責分工

| 文件 | 回答的問題 |
|------|-----------|
| SPRINT_18_PLAN.md | 做什麼？為什麼？多少 SP？ |
| **SPRINT_18_DETAILED_EXECUTION_PLAN.md** | **如何做？何時做？風險？** |
| SPRINT_18_TASKS.md | 哪些任務？完成狀態？ |

---

## 9. 成功標準 (Definition of Success)

### 9.1 Sprint 18 成功標準

- [ ] US-001 Flyway 正式啟用，532 tests 100% 通過
- [ ] US-002 前端 type-check 啟用且驗證有效
- [ ] US-003 M08 新功能完成（至少 1 個）
- [ ] US-004 ErrorCode 評估文件產出
- [ ] US-005 Buffer 適當運用
- [ ] Sprint 18 Review + Retro 文件完成
- [ ] Sprint 18 Release 不可跳過
- [ ] 所有 commit 經過 pre-commit hook CI 驗證

### 9.2 本文件成功標準

- [ ] Dev 每天都知道「今天該做什麼」
- [ ] 遇到問題時可參考決策樹
- [ ] 風險有預先識別的緩解措施
- [ ] Release 流程有明確指引

---

## 10. 確認簽核

| 角色 | 確認狀態 | 簽核日期 | 備註 |
|------|----------|----------|------|
| Human User | ⏳ 待確認 | - | - |
| PM/PO (Victoria) | ⏳ 待確認 | - | - |
| SD (Marcus) | ⏳ 待確認 | - | - |
| Dev (David) | ⏳ 待確認 | - | - |
| Architect (Claude Code) | ✅ 已產出 | 2026-06-22 | 初版建立 |

---

**文件版本**: v1.0
**最後更新**: 2026-06-22
**基於 AISDLC**: v0.09

## 📝 文件修訂紀錄

| 版本 | 日期 | 作者 | 變更內容 |
|------|------|------|----------|
| v1.0 | 2026-06-22 | Claude Code (Architect) | 初版建立，補充 SPRINT_18_PLAN.md 的執行細節，包含：每日時程、執行步驟、風險管理、檢查點、決策樹 |
