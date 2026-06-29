# Entity ↔ Migration 一致性盤點報告 / Entity-Migration Consistency Audit

> **文件類型**: 程式碼品質 / 技術債盤點
> **建立日期**: 2026-06-29
> **對應**: Sprint 25 US-002 / AI-902（P1）
> **盤點範圍**: 51 個 `@Entity`、55 個 Flyway migration（V1~V55）
> **判定工具**: `make validate-schema`（US-001 守門關卡，[SCHEMA_DRIFT_GATE.md](../08_deployment/SCHEMA_DRIFT_GATE.md)）

---

## 1. 目的

Sprint 24 因 entity 與 Flyway migration 的 schema 漂移投入 9 個計畫外 commit 救火（V48~V55）。US-001 已建立守門關卡攔截「會讓 backend 啟動失敗」的硬漂移。US-002 在此之上做**全庫盤點**，建立 entity↔migration 對照基線、確認無殘留漂移、固化防復發慣例。

---

## 2. 方法（兩層）

| 層 | 手段 | 涵蓋 |
|----|------|------|
| **權威硬檢查** | `make validate-schema`：乾淨 PostgreSQL + Flyway 跑 V1~V55 + `ddl-auto=validate` 啟動 backend | 缺表、缺欄位、型別不符（Hibernate validate 邊界） |
| **人工聚焦盤點** | 針對 `validate` 的歷史地雷型別（jsonb / `TEXT[]` / `SqlTypes.ARRAY`）逐一比對 entity↔DDL；交叉比對表名孤兒 | 集合型別映射一致性、慣例一致性 |

> 純量欄位（`UUID`/`String`/`Instant`/`Integer`/enum-as-string）由 `validate` 完整把關，本盤點不重複列舉（Rule 2 簡潔優先）。

---

## 3. 基線結果（2026-06-29）

```
make validate-schema → exit 0
[schema-gate] ✅ backend 啟動成功 → entity 與 Flyway schema 對齊，無漂移
```

**結論：以 GitHub E2E 相同條件（validate + Flyway + 乾淨 DB）啟動，51 個 entity 全數通過 schema 驗證，無硬漂移。**

| 盤點項 | 結果 |
|--------|------|
| Entity 表名 → CREATE TABLE migration 對照 | ✅ 50/50 皆有對應（**零孤兒表**） |
| jsonb 欄位 → migration 支撐 | ✅ 22 欄位全部有對應 JSONB DDL |
| 歷史 `TEXT[]` 欄位 | ✅ 3 個全部已轉 jsonb（見 §5） |
| `@JdbcTypeCode(SqlTypes.ARRAY)` 殘留 | ✅ **0 個**（V55 已轉換最後一個 rooms.amenities） |

---

## 4. jsonb 集合欄位對照（21 entity / 22 欄位）

歷史漂移幾乎全集中於 jsonb 集合欄位。全庫 jsonb 欄位與其建立 migration 對照如下（皆通過 validate）：

| 建立 migration | Entity（表） | 備註 |
|---------------|-------------|------|
| **V1**（大寫 `JSONB`） | Tenant(tenants)、User(users)、TenantFeatureToggle(tenant_feature_toggles)、Listing(listings: tags+metadata)、PricingRule(pricing_rules)、Order(orders)、Booking(bookings: status_flags+metadata)、Payment(payments) | V1 用大寫 `JSONB DEFAULT`，與 entity `@JdbcTypeCode(SqlTypes.JSON)` 對齊 |
| V10 | Post(posts) | |
| V28 | NotificationTemplate(notification_templates) | |
| V30 / V31 | Review(reviews: images)、BookingReview(booking_reviews: images) | |
| V45 / V46 | Conversation(conversations: metadata)、Message(messages: attachments+metadata) | Sprint 24 M10 新增 |
| V49 | Banner(cms_banners)、ContentPage(cms_pages: sections) | |
| V51 | **Logistics(logistics: logistics_data)** | ⚠️ 見 §6 慣例不一致 |
| V52 | Notification(notifications: data) | |
| 後續轉換型 | Room(rooms.amenities)、ArticleVersion(article_versions.tags)、MediaAsset(media_assets.tags) | 原為 `TEXT[]`，見 §5 |

---

## 5. 歷史 `TEXT[]` → jsonb 轉換（已全部封閉）

Sprint 24 漂移根因：部分集合欄位 DDL 建為 PostgreSQL 原生 `TEXT[]`，但 entity 以 `SqlTypes.JSON`(jsonb) 映射。此映射在 Hibernate 6.4 **跨平台不一致**（macOS validate 對 `text[]` 放行、Linux runner 期望 jsonb 而失敗），導致「本機過、GitHub E2E 爆」。

| 欄位 | 建為 TEXT[] | 轉 jsonb 的 migration | 狀態 |
|------|-----------|----------------------|------|
| `rooms.amenities` | V1 | **V55** | ✅ 已轉換 |
| `article_versions.tags` | V32 | **V48** | ✅ 已轉換 |
| `media_assets.tags` | V38 | **V54** | ✅ 已轉換 |

> V55 註解明載：**rooms.amenities 是全庫最後一個 `@JdbcTypeCode(SqlTypes.ARRAY)` 欄位**。轉換後全庫集合一律 jsonb，無原生陣列映射殘留（本盤點 grep 複驗：`SqlTypes.ARRAY` = 0 處）。
>
> 註：migration 中其餘 `ARRAY[...]::text[]` 皆為 enum 的 `CHECK` 約束（V49/V50/V51/V52），非陣列欄位，無漂移風險。

---

## 6. 發現的慣例不一致（非漂移，建議統一）

| 項目 | 現況 | 建議 |
|------|------|------|
| `Logistics.logisticsData` | 型別 `String` + `@Column(columnDefinition = "jsonb")`，**缺** `@JdbcTypeCode(SqlTypes.JSON)` | 其餘 20 個 jsonb 欄位皆為 `Map<String,Object>`/`List` + `@JdbcTypeCode(SqlTypes.JSON)`。Logistics 以 String 存放 jsonb 字串目前 validate 通過、功能正常，但與全庫慣例不一致。**列為低優先技術債**（DEF），不在本 US 範圍內修改（Rule 3 精準改動）。 |

> 此項僅記錄，不在 US-002 動工（避免擴大變更面）。若未來 Logistics 需以結構化方式讀寫該欄位，再一併改為 `Map` + `@JdbcTypeCode`。

---

## 7. 防漂移慣例（固化，杜絕復發）

新增/修改 entity 或 migration 時遵守：

1. **集合 / 結構化欄位一律 jsonb**：entity 用 `Map`/`List` + `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(columnDefinition = "jsonb")`；migration 用 `JSONB`。**禁用** `@JdbcTypeCode(SqlTypes.ARRAY)` 與原生 `TEXT[]` 欄位（跨平台 validate 不一致）。
2. **每個 `@Entity` 必有對應 CREATE TABLE migration**；新欄位必附 `ALTER TABLE` migration（檔名接續，目前最新 V55，下一個 V56）。
3. **改動 `@Entity` 欄位或新增/修改 migration 後、push 前，必跑 `make validate-schema`**（填補 `make validate-all` 走 `ddl-auto=update` 抓不到漂移的盲點）。
4. **enum 以字串存放**（`@Enumerated(EnumType.STRING)`），migration 以 `VARCHAR + CHECK` 約束，勿用 PostgreSQL enum 型別。

---

## 8. 結論

- ✅ **零殘留硬漂移**：51 entity 全數通過 validate（守門關卡 exit 0）。
- ✅ **零孤兒表**、**零 `SqlTypes.ARRAY` 殘留**、3 個歷史 `TEXT[]` 全部封閉。
- ⚠️ **1 項低優先慣例不一致**（Logistics jsonb 映射寫法），列為 DEF 技術債，不在本 US 動工。
- 📌 **防漂移慣例已固化**（§7），搭配 US-001 守門關卡形成「慣例 + 自動關卡」雙重防線。

---

**文件版本**: v1.0
**建立日期**: 2026-06-29
**對應 Action Item**: AI-902（Sprint 24 Retro）
**建立者**: Dev David + Claude Code
