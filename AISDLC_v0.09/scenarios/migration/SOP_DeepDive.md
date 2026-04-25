# Migration 技術棧遷移 - 深度技術指南
# Deep Dive Technical Guide

**版本**: v0.09
**最後更新**: 2026-04-17
**適用對象**: 系統架構師、資深開發者、Tech Lead、遷移專案負責人
**建議閱讀**: 先閱讀 SOP_QuickRef.md 和 SOP.md
**文檔類型**: 技術參考、最佳實踐、深度分析

---

## 📚 文檔說明

### 何時閱讀此文檔

✅ **適合閱讀的情況**:
- 規劃大規模全棧遷移（X-Large 規模）
- 需要零停機時間的生產系統遷移
- 複雜的多業務域系統遷移
- 設計並行運行（Parallel Running）策略
- 處理大量 Stored Procedure 遷移
- 制定回滾策略與回滾演練方案

❌ **不建議閱讀的情況**:
- 初次進行遷移（請閱讀 SOP.md）
- 快速查閱遷移步驟（請閱讀 SOP_QuickRef.md）
- 技術棧語法對照（請閱讀 Refactoring SOP_DeepDive Part 11）

### 與 Refactoring SOP_DeepDive 的分工

| 文檔 | 涵蓋內容 |
|------|---------|
| **本文檔（Migration DeepDive）** | 遷移策略、並行運行、回滾設計、風險管理、資料遷移模式、多業務域遷移 |
| **[Refactoring DeepDive Part 11](../refactoring/SOP_DeepDive.md#part-11)** | 技術語法對照（Oracle→PG、Python→Java、Vue→React）、跨系統驗證技術 |

### 文檔結構

```
Part 1: 遷移策略模式
Part 2: 並行運行架構深度設計
Part 3: 資料遷移模式目錄
Part 4: 多業務域 DDD 遷移深度指南
Part 5: 回滾策略與演練
Part 6: 遷移風險管理
Part 7: 大規模遷移專案管理
Part 8: Troubleshooting Guide
```

### SOP 階段與 Part 對應表

| SOP 階段 | 對應 Part |
|---------|----------|
| 階段 1 現況分析 | Part 4（多業務域）、Part 6（風險識別） |
| 階段 2 遷移架構設計 | Part 1（策略選擇）、Part 2（並行設計） |
| 階段 3 資料庫遷移 | Part 3（資料遷移模式） |
| 階段 7 驗證與測試 | Part 2.5（驗證技術） |
| 階段 8 部署與切換 | Part 5（回滾策略） |
| 大型專案 | Part 7（專案管理） |

---

## Part 1: 遷移策略模式

### 1.1 六大遷移策略選擇指南

#### Strangler Fig Pattern（勒殺無花果模式）

**適用情境**: 大型 Monolith 逐步遷移至新技術棧

```
                    ┌─────────────────────────────┐
用戶請求            │         Strangler Façade       │
────────────────►  │   (Nginx / API Gateway)        │
                   │                                 │
                   │  /api/orders  ──►  新系統 (Java) │
                   │  /api/products ──► 舊系統 (Python)│
                   │  /api/users   ──► 新系統 (Java)  │
                   └─────────────────────────────────┘
```

**特點**:
- ✅ 漸進式遷移，低風險
- ✅ 可隨時暫停/回滾單個模組
- ❌ 需維護 Façade 路由配置
- ❌ 新舊系統並存期間資料一致性複雜

**實施步驟**:
1. 在舊系統前建立 API Gateway / Nginx 路由層
2. 從邊緣模組開始，逐模組新系統取代舊系統
3. Gateway 路由表從舊系統 endpoint 逐步切換至新系統
4. 舊模組全部取代後，拆除 Gateway 路由層

---

#### Branch by Abstraction（抽象分支模式）

**適用情境**: 需要在同一代碼庫中並行開發新舊實作

```python
# 抽象介面（不變）
class PaymentGateway(ABC):
    @abstractmethod
    def charge(self, amount: Decimal, card: CardInfo) -> PaymentResult:
        pass

# 舊實作（保留）
class LegacyPaymentGateway(PaymentGateway):
    def charge(self, amount, card):
        return self._old_stripe_sdk.charge(amount, card)

# 新實作（逐步完善）
class NewPaymentGateway(PaymentGateway):
    def charge(self, amount, card):
        return self._stripe_v3_sdk.charge(amount, card)

# Feature Flag 控制
gateway = NewPaymentGateway() if feature_flags.is_enabled("new_payment") \
    else LegacyPaymentGateway()
```

**特點**:
- ✅ 同一代碼庫，不需要 Façade
- ✅ Feature Flag 可精細控制切換
- ❌ 需要抽象介面的預先設計

---

#### Expand-Contract Pattern（展開-收縮模式）

**適用情境**: 資料庫 Schema 破壞性變更（不可停機）

```
Phase 1 - Expand（展開）:
  新欄位 new_column 加入（nullable）
  舊代碼只寫 old_column
  新代碼同時寫 old_column 和 new_column

Phase 2 - Migrate（遷移）:
  背景任務將 old_column → new_column
  漸進式遷移，不鎖表

Phase 3 - Contract（收縮）:
  代碼只讀 new_column
  等所有實例部署完成

Phase 4 - Cleanup（清理）:
  移除 old_column
  移除雙寫代碼
```

---

#### Blue-Green Deployment（藍綠部署）

**適用情境**: 需要即時切換能力的小中規模遷移

```
             ┌──────────────────────────────────────┐
Load Balancer│                                      │
             │  Blue（舊系統）   Green（新系統）     │
             │  ● 正在服務       ○ 準備就緒          │
             │                                      │
             │  一鍵切換：Blue ──► Green              │
             │  問題回滾：Green ──► Blue              │
             └──────────────────────────────────────┘
```

**特點**:
- ✅ 切換瞬間完成，RTO ≈ 0
- ✅ 問題時可即時回滾
- ❌ 需要 2x 基礎設施成本
- ❌ 資料庫必須支援兩個版本同時運行

---

#### Canary Deployment（金絲雀部署）

**適用情境**: 大規模生產環境，需要漸進驗證的遷移

```
100% 流量 → 舊系統
    ↓ 5% 切換驗證 (1 小時)
95% 舊 + 5% 新
    ↓ 25% 切換驗證 (2 小時)
75% 舊 + 25% 新
    ↓ 50% 切換驗證 (4 小時)
50% 舊 + 50% 新
    ↓ 100% 切換
0% 舊 + 100% 新
```

**Rollback Gate（自動回滾觸發）**:
```yaml
rollback_gate:
  error_rate_threshold: 1%      # 錯誤率 > 1% 觸發
  p99_latency_threshold: 500ms  # P99 延遲 > 500ms 觸發
  success_rate_threshold: 99%   # 成功率 < 99% 觸發
  observation_window: 300s      # 觀察視窗 5 分鐘
```

---

#### Parallel Run（並行運行）

**適用情境**: 金融/電商系統，需要 100% 資料一致性驗證

詳細設計請參考 **Part 2: 並行運行架構深度設計**。

---

### 1.2 策略選擇決策樹

```
系統是否可停機？
├── 是（維護窗口）
│   └── 規模大小？
│       ├── 小（< 10 個模組）→ Blue-Green
│       └── 大（> 10 個模組）→ Canary
│
└── 否（不可停機）
    └── 資料是否有金融/合規要求？
        ├── 是 → Parallel Run + Expand-Contract
        └── 否
            └── 是否需要逐模組替換？
                ├── 是 → Strangler Fig
                └── 否 → Branch by Abstraction + Canary
```

---

## Part 2: 並行運行架構深度設計

### 2.1 並行運行模式分類

| 模式 | 說明 | 適用情境 |
|------|------|---------|
| **Shadow Mode** | 新系統接收流量但不返回結果（純觀察） | 初期驗證，不影響用戶 |
| **Active-Active** | 新舊系統同時處理，比對結果 | 金融系統，需要 100% 比對 |
| **Active-Passive** | 新系統接收流量，舊系統備援 | 信心漸建階段 |
| **Read-Write Split** | 讀流量導向新系統，寫流量導向舊系統 | 讀多寫少系統 |

---

### 2.2 雙寫（Dual-Write）架構設計

```
                    Application Layer
                         │
                   ┌─────▼─────┐
                   │ Write API  │
                   └─────┬─────┘
                         │ 雙寫
              ┌──────────┴──────────┐
              ▼                     ▼
      ┌───────────────┐   ┌───────────────┐
      │ 舊 DB (Oracle) │   │ 新 DB (PG)    │
      │   (Primary)   │   │  (Secondary)  │
      └───────────────┘   └───────────────┘
              │                     │
              └──────────┬──────────┘
                         ▼
              ┌───────────────────┐
              │ 資料一致性校驗服務  │
              │ (Reconciliation)   │
              └───────────────────┘
```

**雙寫實作模式**:

```python
class DualWriteOrderService:
    def __init__(self, legacy_repo, new_repo, reconciler):
        self.legacy_repo = legacy_repo
        self.new_repo = new_repo
        self.reconciler = reconciler

    async def create_order(self, order_data: OrderDTO):
        # 1. 先寫入主系統（舊系統），確保業務繼續
        legacy_result = await self.legacy_repo.create(order_data)
        
        try:
            # 2. 寫入新系統（異步，不阻塞主流程）
            new_result = await self.new_repo.create(order_data)
            
            # 3. 記錄比對
            await self.reconciler.record(
                entity_id=legacy_result.id,
                legacy_checksum=legacy_result.checksum(),
                new_checksum=new_result.checksum()
            )
        except Exception as e:
            # 新系統失敗不影響業務（記錄告警）
            await self.alert_service.notify(
                severity="WARNING",
                message=f"New DB write failed: {e}",
                entity_id=legacy_result.id
            )
        
        return legacy_result  # 始終返回主系統結果
```

---

### 2.3 CDC（Change Data Capture）同步

**Debezium + Kafka CDC 架構**:

```yaml
# debezium-connector.yaml
name: oracle-cdc-connector
config:
  connector.class: io.debezium.connector.oracle.OracleConnector
  database.hostname: oracle-host
  database.port: 1521
  database.dbname: PROD
  table.include.list: SCHEMA.ORDERS,SCHEMA.PRODUCTS,SCHEMA.INVENTORY
  
  # 轉換配置
  transforms: unwrap
  transforms.unwrap.type: io.debezium.transforms.ExtractNewRecordState
  transforms.unwrap.drop.tombstones: false
```

**CDC Consumer（PostgreSQL 同步器）**:

```python
class CDCSyncConsumer:
    def __init__(self, pg_repo, conflict_resolver):
        self.pg_repo = pg_repo
        self.conflict_resolver = conflict_resolver

    async def process_event(self, event: CDCEvent):
        if event.operation == "INSERT":
            await self.pg_repo.insert(event.after)
        elif event.operation == "UPDATE":
            existing = await self.pg_repo.find(event.key)
            if existing and existing.updated_at > event.ts:
                # 衝突：新 DB 的記錄比 CDC 事件更新
                await self.conflict_resolver.resolve(existing, event)
            else:
                await self.pg_repo.update(event.key, event.after)
        elif event.operation == "DELETE":
            await self.pg_repo.soft_delete(event.key)
```

---

### 2.4 並行運行驗證策略

**一致性校驗服務設計**:

```python
class ReconciliationService:
    """
    背景任務，定期比對新舊系統的資料差異。
    """
    
    async def run_reconciliation(
        self,
        entity_type: str,
        batch_size: int = 1000
    ) -> ReconciliationReport:
        report = ReconciliationReport(entity_type=entity_type)
        
        # 分批比對
        async for batch in self.legacy_repo.scan_batches(batch_size):
            for entity in batch:
                new_entity = await self.new_repo.find(entity.id)
                
                if new_entity is None:
                    report.add_missing(entity.id)
                elif not self._is_equivalent(entity, new_entity):
                    report.add_mismatch(
                        entity.id,
                        self._diff(entity, new_entity)
                    )
        
        return report
    
    def _is_equivalent(self, legacy_entity, new_entity) -> bool:
        """業務邏輯等價比較（非完全相同，允許無關欄位差異）"""
        key_fields = ["order_id", "amount", "status", "customer_id"]
        return all(
            getattr(legacy_entity, f) == getattr(new_entity, f)
            for f in key_fields
        )
```

---

## Part 3: 資料遷移模式目錄

### 3.1 大量資料遷移模式

#### Chunked Migration（分塊遷移）

**適用**: 資料量 > 1M 行，需要控制遷移速度和影響

```python
class ChunkedMigrationTask:
    def __init__(self, chunk_size=10_000, delay_ms=100):
        self.chunk_size = chunk_size
        self.delay_ms = delay_ms
    
    async def migrate(self, table_name: str, transformer):
        total = await self.source_db.count(table_name)
        migrated = 0
        
        while migrated < total:
            batch = await self.source_db.fetch(
                table_name,
                offset=migrated,
                limit=self.chunk_size
            )
            
            transformed = [transformer(row) for row in batch]
            await self.target_db.bulk_insert(table_name, transformed)
            
            migrated += len(batch)
            
            # 速率控制：避免影響生產系統
            await asyncio.sleep(self.delay_ms / 1000)
            
            # 進度記錄（支援中斷後恢復）
            await self.checkpoint_store.save(table_name, migrated)
```

---

#### Zero-Downtime Schema Migration 五步驟

```
步驟 1: Add Column（新增欄位，nullable）
  ALTER TABLE orders ADD COLUMN new_status VARCHAR(50);
  -- 舊代碼繼續寫 old_status，新代碼雙寫

步驟 2: Backfill（背景補填）
  UPDATE orders SET new_status = old_status WHERE new_status IS NULL
  LIMIT 10000;  -- 分批，避免長事務鎖表

步驟 3: NOT NULL Constraint（加約束）
  ALTER TABLE orders ALTER COLUMN new_status SET NOT NULL;
  -- 確認所有數據已填充

步驟 4: Switch Read/Write（切換讀寫）
  應用程式切換為讀寫 new_status，停止讀寫 old_status

步驟 5: Drop Old Column（清理舊欄位）
  ALTER TABLE orders DROP COLUMN old_status;
  -- 等所有應用實例部署完新代碼後執行
```

---

### 3.2 資料驗證模式

#### 三層驗證策略

```
Layer 1 - 量化驗證（快速）
  ├── 行數比對（每表）
  ├── 總金額比對（金融欄位）
  ├── 最大/最小/平均值比對
  └── NULL 值數量比對

Layer 2 - 抽樣驗證（中速）
  ├── 隨機抽樣 5%
  ├── 邊界值記錄（最新 100 筆）
  ├── 高風險記錄（大金額訂單）
  └── 業務關鍵記錄（VIP 用戶）

Layer 3 - 全量比對（慢，按需）
  ├── 金融核心資料全量比對
  ├── 法規要求資料全量比對
  └── 遷移完成前最終驗證
```

---

## Part 4: 多業務域 DDD 遷移深度指南

### 4.1 Bounded Context 遷移優先順序策略

**依賴圖驅動的遷移順序**:

```
依賴分析原則：
  - 被依賴多的 Context（Core Domain）→ 最後遷移
  - 邊緣 Context（Supporting Domain）→ 優先遷移
  - 基礎設施 Context（Infrastructure）→ 最先遷移

典型電商多業務域遷移順序：
  1. UserCtx（基礎，其他都依賴）← 最先遷移（影響範圍最大，但也最穩定）
  2. NotificationCtx（無依賴其他業務 Context）
  3. FileCtx（無業務邏輯，純基礎設施）
  4. ProductCtx（被 Order、Cart 依賴，但自身邊界清晰）
  5. CartCtx（依賴 Product，不被其他依賴）
  6. OrderCtx（核心，依賴 Product、Payment）← 最後遷移
  7. PaymentCtx（最高風險，合規要求）← 獨立 Phasing
```

---

### 4.2 跨 Context 通信遷移策略

**遷移期間的通信模式轉換**:

```
遷移前（Monolith）：
  Order 直接呼叫 Payment 服務層
  直接資料庫 JOIN 查詢

遷移中（混合期）：
  新 OrderCtx ──HTTP──► 舊 PaymentCtx（臨時適配器）
  舊 OrderCtx ──直接──► 舊 PaymentCtx（不變）

遷移後（Microservice）：
  新 OrderCtx ──Event──► 新 PaymentCtx
  (Domain Event: OrderPlaced → PaymentService handles)
```

**臨時適配器（Anti-Corruption Layer）實作**:

```java
@Component
public class LegacyPaymentAdapter implements PaymentService {
    
    private final LegacyPaymentFacade legacyFacade;
    
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        // 新格式 → 舊格式轉換
        LegacyPaymentRequest legacyRequest = toLegacyFormat(request);
        LegacyPaymentResult legacyResult = legacyFacade.charge(legacyRequest);
        
        // 舊格式 → 新格式轉換
        return toNewFormat(legacyResult);
    }
    
    private LegacyPaymentRequest toLegacyFormat(PaymentRequest req) {
        return new LegacyPaymentRequest(
            req.getAmount().multiply(BigDecimal.valueOf(100)).intValue(), // 元 → 分
            req.getCardToken(),
            "ORDER_" + req.getOrderId() // 舊系統要求特定前綴
        );
    }
}
```

---

### 4.3 共用模組的遷移注意事項

```
⚠️ 常見陷阱：共用 User 資料的多 Context 遷移

問題：
  所有 Context 都依賴 User 資料
  UserCtx 最先遷移時，其他 Context 仍在舊系統

解決方案：
  方案 A: User Replication API
    新 UserCtx 暴露讀 API
    舊系統通過 HTTP 讀取新 UserCtx（單向依賴）

  方案 B: Event-Driven User Sync
    新 UserCtx 發布 UserUpdated Event
    舊系統訂閱並更新本地 User 快取

  方案 C: UserCtx 最後遷移
    所有 Context 遷移完成後，再遷移 UserCtx
    最安全但遷移周期最長
```

---

## Part 5: 回滾策略與演練

### 5.1 分層回滾策略

```
Layer 3（Canary 回滾）: 2 分鐘
  觸發：自動（錯誤率 > 1%）
  動作：Load Balancer 將流量 100% 切回舊系統
  DB：不需要變更（雙寫已確保資料一致）

Layer 2（應用回滾）: 15 分鐘
  觸發：手動（發現業務邏輯錯誤）
  動作：部署舊版 Docker Image
  DB：執行反向 Schema Migration（如已有）

Layer 1（完整回滾）: 1-4 小時
  觸發：重大系統問題或資料不一致
  動作：全面切回舊系統
  DB：從備份恢復或執行 Rollback SQL
```

---

### 5.2 回滾演練 Checklist

```
□ 回滾演練環境準備
  □ Staging 環境模擬 50% Canary 狀態
  □ 資料庫雙寫已啟動
  □ 監控告警已配置

□ 演練場景 1：Canary 自動回滾
  □ 觸發錯誤率 > 1%（注入故障）
  □ 驗證 Load Balancer 自動切換（< 2 分鐘）
  □ 驗證告警通知送達

□ 演練場景 2：手動觸發回滾
  □ 執行回滾命令
  □ 計時驗證 RTO 是否 ≤ 15 分鐘
  □ 驗證業務功能恢復

□ 演練場景 3：資料庫回滾
  □ 執行 Rollback SQL
  □ 驗證資料完整性
  □ 計時驗證 RTO ≤ 4 小時

□ 演練結果記錄
  □ 各層 RTO 實測值
  □ 問題點與改善項
```

---

### 5.3 每個 Migration PR 必附的回滾腳本模板

```bash
#!/bin/bash
# rollback.sh - Migration PR 必備回滾腳本
# 使用方式: ./rollback.sh [layer] [component]
# 範例: ./rollback.sh canary order-service

set -e

LAYER=${1:-canary}
COMPONENT=${2:-all}
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

log() { echo "[$(date '+%H:%M:%S')] $*"; }

case $LAYER in
  canary)
    log "Layer 3 回滾: 切換流量至舊系統"
    kubectl patch ingress migration-ingress \
      --patch '{"metadata":{"annotations":{"nginx.ingress.kubernetes.io/canary-weight":"0"}}}'
    log "✅ Canary 回滾完成"
    ;;
    
  app)
    log "Layer 2 回滾: 部署舊版應用"
    kubectl rollout undo deployment/${COMPONENT}
    kubectl rollout status deployment/${COMPONENT}
    log "✅ 應用回滾完成"
    ;;
    
  db)
    log "Layer 1 回滾: 執行 DB Rollback SQL"
    psql $DATABASE_URL -f "migrations/rollback/${TIMESTAMP}_${COMPONENT}.sql"
    log "✅ DB 回滾完成"
    ;;
    
  *)
    echo "Usage: $0 [canary|app|db] [component]"
    exit 1
    ;;
esac
```

---

## Part 6: 遷移風險管理

### 6.1 風險矩陣

| 風險 | 發生機率 | 影響程度 | 風險等級 | 緩解策略 |
|------|---------|---------|---------|---------|
| 資料遷移不完整 | 中 | 高 | 🔴 高 | 三層驗證 + 全量比對 |
| 新系統效能退化 | 中 | 高 | 🔴 高 | Benchmark Gate + Canary 監控 |
| 業務邏輯遺漏 | 高 | 高 | 🔴 高 | SP 清單 + 系統性驗證 |
| 並行運行資料衝突 | 低 | 高 | 🟡 中 | CDC + Reconciliation 服務 |
| 第三方 API 不相容 | 低 | 中 | 🟡 中 | 沙盒測試 + Contract Test |
| 舊系統退役過早 | 低 | 高 | 🟡 中 | 保留 6 個月 + 流量監控 |
| 合規資料處理不當 | 低 | 極高 | 🔴 高 | 合規審查 + 加密驗證 |

---

### 6.2 里程碑 Gate 設計

```
Gate 1（現況分析完成）: 進入遷移設計的前置條件
  ✅ 舊系統 100% 模組清單
  ✅ SP/Trigger/View 完整清單
  ✅ 業務邏輯文檔（Critical 模組）

Gate 2（架構設計確認）: 進入實作的前置條件
  ✅ 技術棧映射表 100% 完成
  ✅ 並行運行設計確認
  ✅ 回滾策略演練完成
  ✅ Bounded Context 劃分（多業務域時）

Gate 3（UAT 通過）: 進入生產切換的前置條件
  ✅ 業務驗收測試 100% 通過
  ✅ 資料一致性比對差異 ≤ 0.01%
  ✅ 效能對比無退化（或有改善）
  ✅ 安全掃描無 Critical/High 漏洞

Gate 4（Canary 穩定）: 進入 100% 切換的前置條件
  ✅ 5% Canary 運行 1 小時無異常
  ✅ 25% Canary 運行 2 小時無異常
  ✅ 50% Canary 運行 4 小時無異常
```

---

## Part 7: 大規模遷移專案管理

### 7.1 遷移專案計畫模板（X-Large 規模）

```
Phase 0: 準備階段（2-4 週）
  - 現況分析與風險評估
  - 技術棧選型決策
  - 並行運行基礎設施建置
  - 團隊技術培訓

Phase 1: 基礎設施層（2-4 週）
  - CI/CD 4 層 Pipeline 建立
  - 監控告警系統
  - 安全基線建立
  - DB 遷移基礎設施

Phase 2: 資料庫層（4-8 週）
  - Schema 轉換
  - SP/Function 遷移
  - 雙寫機制啟動
  - 資料驗證自動化

Phase 3: 後端層（8-12 週）
  - API 逐模組重新實作
  - 業務邏輯對比驗證
  - Strangler Façade 配置

Phase 4: 前端層（6-10 週）
  - 逐頁面/模組重寫
  - E2E 自動化測試

Phase 5: 新平台層（4-8 週，可與 Phase 4 平行）
  - 行動端開發
  - 硬體整合測試

Phase 6: 整合與切換（2-4 週）
  - UAT
  - Canary 漸進切換
  - 舊系統退役
```

---

### 7.2 遷移進度追蹤指標

| 指標 | 計算方式 | 健康目標 |
|------|---------|---------|
| 模組遷移進度 | 已遷移模組 / 總模組 | 按計畫 ± 10% |
| 資料一致性率 | (總行數 - 差異行數) / 總行數 | ≥ 99.99% |
| 測試覆蓋率 | 已測模組 / 已遷移模組 | ≥ 80% |
| 回滾演練成功率 | 成功演練次數 / 總演練次數 | 100% |
| 技術債引入率 | 新增技術債 / 遷移模組數 | ≤ 5% |

---

## Part 8: Troubleshooting Guide

### 8.1 常見問題：並行運行資料不一致

**症狀**: Reconciliation 報告發現 0.1% 以上的資料差異

**診斷步驟**:

```sql
-- 1. 識別不一致記錄
SELECT o.order_id, o.amount AS legacy_amount, n.amount AS new_amount
FROM legacy.orders o
JOIN new_db.orders n ON o.order_id = n.order_id
WHERE o.amount != n.amount
  AND o.created_at >= NOW() - INTERVAL '24 hours'
LIMIT 100;

-- 2. 確認是否為時序問題（CDC 延遲）
SELECT MAX(event_ts), NOW(), NOW() - MAX(event_ts) AS lag
FROM cdc_events
WHERE table_name = 'orders';

-- 3. 確認雙寫失敗記錄
SELECT * FROM dual_write_failures
WHERE entity_type = 'orders'
  AND created_at >= NOW() - INTERVAL '1 hour';
```

**解決方案**:

| 根本原因 | 解決方案 |
|---------|---------|
| CDC 延遲 > 30 秒 | 增加 Kafka Consumer 實例數，檢查網路頻寬 |
| 雙寫失敗（新 DB 網路問題） | 實作 Outbox Pattern + 重試機制 |
| 業務邏輯差異 | 找出不一致記錄的業務場景，修正新系統邏輯 |
| 時間戳衝突 | 實作 Last-Write-Wins + 版本號機制 |

---

### 8.2 常見問題：SP 遷移後業務計算差異

**症狀**: 新系統訂單金額計算結果與舊系統不同（差異 < 0.01%）

**診斷**:

```python
# 建立 SP 行為等價測試
def test_calculate_discount_equivalence():
    test_cases = generate_boundary_test_cases()
    
    for case in test_cases:
        legacy_result = legacy_sp.calculate_discount(case)
        new_result = new_service.calculate_discount(case)
        
        assert abs(legacy_result - new_result) < Decimal('0.01'), \
            f"差異 case: {case}, legacy: {legacy_result}, new: {new_result}"
```

**常見根本原因**:

| 問題 | 描述 | 修正方式 |
|------|------|---------|
| 浮點精度差異 | Python `float` vs Java `double` | 使用 `Decimal` / `BigDecimal` |
| 捨入邏輯差異 | 舊 SP 用 `ROUND(x,2)` | 確認 Java 使用 `HALF_UP` 模式 |
| 稅率計算順序 | 先乘後除 vs 先除後乘 | 對齊計算公式 |
| NULL 值處理 | Oracle NVL vs Java Optional | 明確定義 NULL 語義 |

---

### 8.3 常見問題：Canary 部署錯誤率偏高

**診斷 Dashboard（Grafana 查詢）**:

```promql
# Canary vs Stable 錯誤率比對
sum(rate(http_requests_total{status=~"5..",deployment="canary"}[5m]))
/
sum(rate(http_requests_total{deployment="canary"}[5m]))

# vs

sum(rate(http_requests_total{status=~"5..",deployment="stable"}[5m]))
/
sum(rate(http_requests_total{deployment="stable"}[5m]))
```

**快速分流**:

```
錯誤率 > 1% 的 Canary 問題分類：

1. 只在 Canary 發生 → 新代碼 Bug（回滾後修復）
2. Canary 和 Stable 都增加 → 外部依賴問題（非 Canary 本身）
3. 特定 API 端點 → 業務邏輯問題（針對性 Hotfix）
4. 特定用戶群 → 邊界數據問題（找出邊界案例）
```

---

## 📚 相關文檔

- [Migration SOP.md](./SOP.md) - 標準作業程序
- [Migration SOP_QuickRef.md](./SOP_QuickRef.md) - 快速參考
- [Refactoring SOP_DeepDive Part 11](../refactoring/SOP_DeepDive.md) - 技術語法對照（Oracle→PG、Python→Java、Vue→React）
- [devops/SOP.md](../devops/SOP.md) - CI/CD 4 層 Pipeline 詳細設計
- [migration-planning-flow.md](../../workflow/scenario-specific/migration-planning-flow.md)

---

**文檔版本**: v0.09
**建立日期**: 2026-04-17
**適用範圍**: AISDLC v0.09 Migration 情境
