# Sprint 111 Plan — DEF-062：資料庫 schema 文件全面對齊實作（ER-005）

**Sprint**: Sprint 111
**日期**: 2026-09-02
**AI 編號**: AI-2445
**主題**: Sprint 110 收尾時記下的 DEF-062，原以為只是 `tenants` 一張表的欄位落差。量完範圍後發現是**兩份文件、29 張表、254 個欄位級錯誤**。

---

## 1. 先量範圍（這是 DEF-062 的第一步，也是決定修法的前提）

建立權威基準的方法：起一個乾淨 `postgres:18-alpine`，**依版本序套完全部 70 個 Flyway 遷移**
（0 個失敗，建出 65 張表），再以 `information_schema` 逐表比對兩份文件。

| 文件 | 漂移張數 | 幽靈欄位<br>(文件有、實作無) | 缺載欄位<br>(實作有、文件無) | 性質 |
|------|---------|------------------------------|------------------------------|------|
| `SRD_Database_Schema.md` §2 | **16 / 16** | 117 | 55 | **從未對過** |
| PRD §8.2 | **12 / 13** | 21 | 61 | **落後** |

**兩者性質不同，這決定了修法不同：**

- **SRD** 不是「舊了」，是設計稿從未與實作對齊過——`product_inventory` 連**主鍵都是錯的**
  （文件 `id`，實作 `sku_id` 且掛在 `product_skus` 之下），`user_profiles`／`pricing_overrides`
  **兩張表從未被實作**。連 §1 ERD、§3.3 狀態機、§4 隔離機制、§5 索引、§6 約束全都建在幽靈欄位上。
- **PRD** 多數是後續 migration 新增欄位沒回填（Stripe Connect、M16 採購審批、S86 結算逆轉），
  加上 12 處欄位改名。

> ⚠️ **DEF-062 原記錄低估了範圍**。S110 寫的是「`tenants` 有 8 處落差…可能不只一張表，未查證」。
> 實際是**每一張都錯**。這是「沒查證就先估規模」的代價——記錄時該估的是「未知」，不是「大概 3 SP」。

---

## 2. 使用者決策

呈上三案（重寫＋封存原稿／只重寫／全部只加註警告），使用者選 **重寫 + 原稿存 archive + 自動守門**。

這與 S110「SRD 設計稿加註不改寫」看似相反，實則一致：S110 明確把全表對照**留給 DEF-062**，
而「保留設計意圖」的訴求由 `archive/` 滿足——**兩個需求各自成立，不必二選一**。

---

## 3. 交付

### 3.1 `SRD_Database_Schema.md` → v2.0（全文重寫）

| 章節 | 變更 |
|------|------|
| 標頭／元數據 | 標明權威來源為 Flyway 遷移；多租戶策略更正為「每個查詢明確過濾（**未**使用 Hibernate Filter）」 |
| §1 ERD | 依實作重繪；標出 `product_inventory` 掛在 `product_skus` 之下、兩張未實作表 |
| §2 資料表 | **14 張表的 DDL 全部自 DB 匯出**（欄位／型別／預設值／約束／索引）；`user_profiles`、`pricing_overrides` 改標為未實作並說明由誰取代 |
| §3.1 訂單狀態機 | 補上遺漏的 `CONFIRMED`（enum 有 9 個值，原圖只畫 8 個） |
| §3.3 租戶狀態機 | 原圖是 `PENDING → APPROVED → ACTIVE`——**第三個版本**，與 PRD 原文和實作都不同。改為 S110 確立的兩表兩階段 |
| §4 多租戶隔離 | **整節重寫**（見 §4 下方） |
| §5 索引／§6 約束 | 改為自 DB 匯出的實測清單 |
| 原設計稿 | 完整封存於 `archive/SRD_Database_Schema_designdraft.md`，雙向連結 |

### 3.2 PRD §8.2 → v1.0.2

12 張表逐欄更正：移除 21 個幽靈欄位、補上 61 個實作欄位、更正 12 處改名
（`cover_image_url`→`featured_image_url`、`operated_by`→`created_by`、`received_qty`→`received_quantity` 等）。
新增勘誤 **ER-005**。

### 3.3 自動守門（新增）

```
scripts/validate-schema-doc.sh      乾淨 DB 套 Flyway → 比對兩份文件
scripts/lib/gen_schema_ddl.py       自 DB 產生可讀 DDL（§2 的內容就是它的輸出）
scripts/lib/check_schema_doc.py     比對邏輯（SRD 比 DDL 全文、PRD 比欄位名集合）
make validate-schema-doc            檢查
make sync-schema-doc                依實際 schema 重新產生 SRD 的 DDL
```

接進 `pre-commit`：**只在動到 Flyway 遷移／SRD schema 文件／PRD 時才跑**（約 20-30 秒，需 docker；
docker 不可用時印警告而不擋）。

> **與既有 `validate-schema.sh` 的分工**：
> `validate-schema` 管 **entity ↔ migration**（程式對得上資料庫嗎）；
> `validate-schema-doc` 管 **migration ↔ 文件**（文件說的是真的嗎）。兩者互補，都不可省。

---

## 4. 途中最有價值的發現：§4 宣稱的租戶隔離機制不存在

原 §4.1／§4.3 寫著「所有查詢自動附加 `tenant_id` 條件」「Hibernate 會自動轉換」，並示範了一個
`TenantAwareEntity` 基底類別。

**查證：全 codebase `@FilterDef`／`@Filter` 零命中，`TenantAwareEntity` 這個類別不存在。**

實際機制是 `TenantContextFilter` 寫入 ThreadLocal + **每個查詢／服務方法自己記得過濾**
（`findByIdAndTenantId`、`TenantContext.getCurrentTenant()` 比對，全 codebase 約 226 處明確呼叫）。

**為什麼這比欄位錯更嚴重**：照原文理解，開發者會以為「entity 有 `tenant_id` 就自動被隔離」，
於是新增 Service 方法時不做租戶檢查。追蹤表上 **DEF-023／024／037／040／041／057**
這一整串「某某方法沒有租戶過濾」的 IDOR 缺陷，正是這個誤解會導致的結果。

§4 已重寫為實際機制，並明寫「漏一個就是一個 IDOR、不會有編譯錯誤也不會有測試失敗」與新增方法時的檢查點。

---

## 5. 驗證

**紅燈四組對照**（守門必須會失敗才算數）：

| 對照組 | 操作 | 期望 | 結果 |
|--------|------|------|------|
| A | 竄改 SRD 文件欄位名（`slug`→`slug_x`） | 攔截 | ✅ `tenants：文件 DDL 與實際 schema 不一致` |
| B | 新增遷移改 schema、文件未同步 | 攔截 | ✅ 同上 |
| C | 把未實作的 `pricing_overrides` 重新宣告成 DDL | 攔截 | ✅ 兩條訊息同時觸發 |
| D | PRD §8.2 刪掉一個真實欄位 | 攔截 | ✅ `PRD §8.2 tenants：漏列實作欄位 connect_charges_enabled` |

四組復原後皆回綠。**最終狀態**：SRD 14/14 表 0 漂移、PRD 13/13 表 0 漂移、幽靈欄位 0、缺載欄位 0。

守門開發過程本身也踩到兩個 bug 並修掉（都由紅燈測試逼出來）：
1. 章節邊界正則只擋 `####`，導致沒有 DDL 的「未實作表」章節一路吃到下一張表的區塊 → 假警報。
2. 改擋 `####` 後仍不夠——`pricing_overrides` 是最後一個 2.x.y 章節，會吃到 §6.1 的 SQL；
   必須擋住 `## / ### / ####` 所有層級。

**未跑全量回歸**：本輪唯一的程式碼變更是 `scripts/`（守門工具鏈）與 `Makefile`，
不動 backend／frontend 生產程式碼；`pre-push` 對「無 backend/frontend 變動」的推送明文放行。

---

## 6. 範圍外（已觀察、刻意未動）

| 項目 | 為什麼不動 |
|------|-----------|
| **其餘 51 張表未收錄進 SRD** | 文件宣告的範圍是「核心表」。本輪把「涵蓋 14/65」明寫在 §1，讓讀者知道缺口在哪，而不是誤以為這就是全部 |
| **`Order.OrderStatus.CONFIRMED` 的產生方式** | `CONFIRMED` 不由付款或物流流程產生，只能經通用 `updateOrderStatus`（`valueOf` 直接寫入）設定；而 `LogisticsService:63` 要求必須是 `CONFIRMED` 才能出貨。「賣家確認後才出貨」是合理設計，**不是缺陷**；是否該有專屬確認端點而非走通用端點，未在本輪判斷 |
| **DB 層缺 CHECK 約束** | 14 張核心表只有 `tenant_members.status` 一個 CHECK。補約束會影響既有資料與遷移，屬架構決策。本輪只陳述現況 |
| **`purchase_order_items` 同時有 `quantity` 與 `ordered_qty`** | 看起來冗餘，但未查證是否有語意差異，不臆測 |
| **3 張表有 `updated_at` 卻無觸發器** | `tenant_feature_toggles`／`pricing_rules`／`product_inventory` 靠 Hibernate 維護，繞過 SQL 直改不會推進。已在 §6.2 標明，不主張補 |

---

## 7. 方法論教訓

1. **「沒查證的規模估計」比沒有估計更糟。** DEF-062 記成「8 處落差、3 SP」，實際是 254 個欄位級錯誤、
   還牽出一個危險的假陳述。當時該寫的是「範圍未知」——**假的精確度會讓下一輪照著錯的規模排程**。
2. **文件債要有守門，否則必定重演。** 這份文件錯了整整 100 個 Sprint 沒被發現，因為沒有任何機制
   會因它錯而失敗。修完文件只是還債，**建立 `validate-schema-doc` 才是止血**。
3. **文件的危害有等級之分。** 欄位名寫錯只是查起來不方便；**宣稱一個不存在的安全機制**會讓人
   不去做該做的檢查。§4 那段比 117 個幽靈欄位加起來更值得修。
4. **「保留歷史」和「文件要正確」不是二選一。** S110 我主張設計稿是決策紀錄不該改寫，
   這輪用 `archive/` 同時滿足兩者——當兩個訴求看似衝突時，先問是不是可以都要。
