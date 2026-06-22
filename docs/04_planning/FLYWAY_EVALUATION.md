# Flyway 正式啟用評估 / Flyway Production Evaluation

> **Sprint**: Sprint 17 → Sprint 18 啟用
> **評估日期**: 2026-06-10
> **啟用日期**: 2026-06-19 (Sprint 17 Release v2026.06.19-01)
> **驗證日期**: 2026-06-22 (Sprint 18 Day 1)
> **負責人**: SD + Dev
> **文件版本**: v1.1

---

## ✅ 啟用狀態 (Production Status)

| 項目 | 啟用前狀態 | **當前狀態** | 驗證 |
|------|-----------|-------------|------|
| Flyway 啟用 | ❌ `enabled: false` | ✅ **`enabled: true`** | `application.yml` 已修改 |
| Schema 管理 | ⚠️ `ddl-auto: update` | ✅ **`ddl-auto: validate`** | `application.yml` 已修改 |
| V38 Migration | ⏳ 計畫中 | ✅ **已建立** | `V38__Consolidate_Media_Assets_Schema.sql` |
| V22 衝突 | 🔴 表名衝突 | ✅ **已標記廢棄** | `V22__Create_Media_Assets_Table.sql` 保留但不執行 |
| 版本控制 | ❌ 無 | ✅ **已建立** | V1 ~ V40 共 40 個 migrations |
| **測試結果** | - | ✅ **541 tests 100% 通過** | `mvn verify -Pintegration-test` |

**Sprint 18 Day 1 驗證結果** (2026-06-22):
```
Unit Tests:      272 tests, 0 Failures, 0 Errors
Integration Tests: 269 tests, 0 Failures, 0 Errors
Total:           541 tests, 0 Failures, 0 Errors
BUILD SUCCESS (14:33 min)
```

---

## 📋 評估摘要 (歷史記錄)

| 項目 | Sprint 17 評估時 | Sprint 18 啟用時 |
|------|----------|---------|
| Flyway 狀態 | **已停用** (`enabled: false`) | ✅ **已啟用** (`enabled: true`) |
| Schema 管理 | **Hibernate ddl-auto: update** | ✅ **`ddl-auto: validate`** |
| Migration 衝突 | V13 vs V22 皆建立 `media_assets` 表 | ✅ V22 已廢棄，V38 統一 schema |
| 版本控制 | 無 | ✅ V1 ~ V40 共 40 個 migrations |

---

## 🔍 目前狀態分析

### 1. Flyway 設定現況

```yaml
# application.yml
flyway:
  enabled: false  # ❌ Flyway 已停用

hibernate:
  ddl-auto: update  # ⚠️ Hibernate 管理 schema
```

**問題**：
- Flyway 停用，所有 migration 檔案 (V1-V37) 未執行
- Hibernate 的 `ddl-auto: update` 會自動修改資料庫結構
- Schema 變更沒有版本控制，無法回滾

### 2. Migration 檔案衝突

| Migration | 內容 | 問題 |
|-----------|------|------|
| **V13** | `CREATE TABLE media_assets` (M15 CMS) | 缺少 `category_id`, `tags` 等欄位 |
| **V22** | `CREATE TABLE media_assets` (M18) | 缺少 `uploader_id`, `original_name` 等欄位 |
| **差異** | 兩個版本 schema 不同 | **命名衝突！** |

**V13 Schema** (`cms.media_assets`):
```sql
- tenant_id, uploader_id (required)
- file_name, original_name
- width, height, duration_seconds
- is_active (無 is_deleted)
```

**V22 Schema** (`media.media_assets`):
```sql
- tenant_id, category_id (optional)
- file_name (無 original_name)
- tags, usage_count, alt_text, title
- is_deleted (無 is_active)
```

### 3. 目前使用的 Entity

[JPA Entity](backend/src/main/java/com/nextkey/ecommerce/domain/model/cms/media/MediaAsset.java) 對應 **V22 Schema**（含 `category_id`, `tags`, `is_deleted`），但 **V22 migration 未執行**。

---

## ⚠️ 風險分析

### Hibernate auto-update 風險

| 風險類型 | 說明 | 嚴重性 |
|----------|------|--------|
| **資料丢失** | Hibernate 可能不小心刪除欄位 | 🔴 高 |
| **無法回滾** | Schema 變更沒有記錄 | 🔴 高 |
| **生產環境危險** | 生產資料變更無預警 | 🔴 高 |
| **團隊協作問題** | 多人同時修改，schema 衝突 | 🟡 中 |

### Migration 衝突風險

| 風險 | 說明 |
|------|------|
| **V13/V22 衝突** | 兩者建立相同名稱的表，Flyway 啟用後會失敗 |
| **Entity 不匹配** | V22 schema 與 V13 不同，可能導致資料不一致 |

---

## 📊 選項分析

### 選項 A: 啟用 Flyway（推薦 ✅）

**優點**：
- Schema 版本控制，可回滾
- 團隊協作清晰，宣告式遷移
- 符合產業標準 DevOps 實踐

**缺點**：
- 需要修復 V13/V22 衝突
- 需要建立乾淨的 migration 基線
- 需停用 Hibernate ddl-auto（改為 `validate` 或 `none`）

**實作步驟**：
1. 停用 Hibernate ddl-auto（改為 `validate`）
2. 修復 V13/V22 衝突（建立 V38 統一 schema）
3. 在測試環境驗證
4. 生產環境手動執行 migration

### 選項 B: 維持 Hibernate auto-update

**優點**：
- 立即可用，無需遷移
- 開發速度快

**缺點**：
- ⚠️ 生產環境高風險
- ⚠️ 無法追蹤 schema 變更
- ⚠️ 無法回滾
- ⚠️ 團隊協作困難

---

## 🎯 建議方案（選項 A）

### Phase 1: 修復 Migration 衝突

**T-004-1**: 分析現有 entity 對應的 schema（已完成 ✅）
- Entity 使用 V22 schema（含 category_id, tags, is_deleted）
- V13 是舊版 M15 schema

**T-004-2**: 建立 V38__Consolidate_Media_Assets.sql
```sql
-- 統一 media_assets 表結構
-- 結合 V13 和 V22 的欄位
CREATE TABLE media_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    uploader_id UUID REFERENCES users(id),
    category_id UUID REFERENCES media_categories(id),
    file_name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255),
    file_path VARCHAR(1000) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    tags TEXT[] DEFAULT '{}',
    usage_count INTEGER DEFAULT 0,
    alt_text VARCHAR(255),
    title VARCHAR(255),
    width INTEGER,
    height INTEGER,
    duration_seconds INTEGER,
    is_active BOOLEAN DEFAULT true,
    is_deleted BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Phase 2: 啟用 Flyway

**T-004-3**: 修改 application.yml
```yaml
flyway:
  enabled: true
  baseline-on-migrate: true
  baseline-version: '37'

hibernate:
  ddl-auto: validate  # 改為 validate，不自動修改
```

**T-004-4**: 驗證測試環境
```bash
mvn test -Dtest=*IntegrationTest
```

---

## 📝 任務清單

| 狀態 | 任務 | 預估時間 |
|------|------|----------|
| ⏳ | T-004-1: 分析 Flyway 設定狀態 | 0.25 SP |
| ⏳ | T-004-2: 檢視 V13 和 V22 migration 內容 | 0.25 SP |
| ⏳ | T-004-3: 評估 Hibernate auto-update vs Flyway 風險 | 0.25 SP |
| ⏳ | T-004-4: 建立 FLYWAY_EVALUATION.md 文件 | 0.25 SP |
| ⏳ | T-004-5: 如選擇啟用 Flyway，建立 V38 migration 腳本 | 0.5 SP |
| ⏳ | T-004-6: 驗證 mvn test + schema 正確性 | 0.5 SP |

**總計**: 2 SP

---

## 🔴 結論

**強烈建議啟用 Flyway**（選項 A）：

1. **生產環境安全**：Schema 版本控制，可回滾
2. **團隊協作**：宣告式 migration，清晰可追蹤
3. **DevOps 最佳實踐**：符合資料庫遷移標準

**需修復的問題**：
- V13 和 V22 的 `media_assets` 表命名衝突
- 需建立統一的 V38 migration
- 需停用 Hibernate ddl-auto（改為 validate）

**風險等級**：🟡 中（修復後為 🟢 低）

---

## 📚 參考資料

- [Flyway 官方文檔](https://flywaydb.org/documentation/)
- [Hibernate Schema Management](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_Guide.html#ch-schema-management)
- [V13__create_media_assets_table.sql](backend/src/main/resources/db/migration/V13__create_media_assets_table.sql)
- [V22__Create_Media_Assets_Table.sql](backend/src/main/resources/db/migration/V22__Create_Media_Assets_Table.sql)