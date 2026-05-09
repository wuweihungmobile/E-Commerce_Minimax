# Sprint 6 Plan QA 驗證報告 v2

> **驗證日期**: 2026-04-29
> **驗證員**: QA (Quincy)
> **文件版本**: v1.4
> **基於文檔**:
> - Sprint 6 Plan (v1.4)
> - TC_M01_Product.md (v1.0)
> - TC_M02_Room.md (v1.0)
> - TC_M06_Booking.md (v1.0)
> - TC_M12_Pricing.md (v1.0)
> - DEFERRED_ITEMS_TRACKER.md (v1.0)

---

## 驗證結果摘要

| 驗證項目 | 結果 | 問題數量 |
|----------|------|----------|
| A. 測試覆蓋度驗證 | ❌ 失敗 | 8 個問題 |
| B. 依賴關係驗證 | ✅ 通過 | 0 個問題 |
| C. Story 完整性驗證 | ✅ 通過 | 0 個問題 |
| D. 風險評估驗證 | ⚠️ 警告 | 1 個問題 |
| E. 文件一致性驗證 | ❌ 失敗 | 5 個問題 |

### 通過項目
- ✅ 依賴關係無循環依賴，順序合理
- ✅ 每個 US 都有對應的 TC 映射
- ✅ SP 估算在合理範圍內

### 失敗項目
- ❌ TC 數量與 Plan 報告不符（差異 13 個 TC）
- ❌ 多處優先級分配不一致
- ❌ DoD 測試通過率目標數量錯誤

### 警告項目
- ⚠️ 風險對策覆蓋範圍不足

---

## 詳細驗證結果

### A. 測試覆蓋度驗證

#### A.1 TC 數量總覽比較

| 模組 | Sprint 6 Plan 報告 | TC 文件實際數量 | 差異 |
|------|-------------------|----------------|------|
| M01 總計 | 延後 UT (8), IT (5), API (7) = 20 | UT (8) + IT (5) + API (7) = 20 | N/A (延後) |
| M02 總計 | 延後 UT (7), IT (6), API (9) = 22 | UT (7) + IT (5) + API (9) = 21 | -1 |
| M06 總計 | 13 P0 AT (執行) + 5 P1 + 0 P2 = 18 | 22 (UT+IT+API+ISO+NFR) | N/A |
| M12 總計 | UT (9), IT (5), API 延後 (6) = 20 | UT (9) + IT (5) + API (6) = 20 | API E2E 差異 |

**發現**: Sprint 6 Plan 報告總 TC 數為 67 個，但 TC 文件共 80 個（不含 M06 E2E 重複計算）。

#### A.2 M02 IT 數量不一致

**問題位置**: TC_M02_Room.md Section 2.1 (房源上架/編輯/下架)

| TC ID | 優先級 | Plan Section 8 | TC 文件 |
|-------|--------|---------------|--------|
| IT-M02-001 | P0 | IT P0=3 | P0 |
| IT-M02-002 | P0 | IT P0=3 | P0 |
| IT-M02-003 | P1 | IT P1=2 | P1 |
| IT-M02-004 | P1 | IT P1=2 | P1 |
| IT-M02-005 | P0 | IT P0=3 | P0 |
| IT-M02-006 | P1 | - | P1 |
| IT-M02-007 | P0 | - | P0 (但移到 Section 4 Redis) |
| IT-M02-008 | P1 | - | P1 (但移到 Section 4 Redis) |

**驗證結果**:
- TC 文件 Section 2.1 只有 5 個 IT (IT-M02-001 ~ IT-M02-005)
- Plan Section 8 矩陣顯示 M02 IT 為 6 個，P0=3, P1=2, P2=1
- 差異原因：IT-M02-007 和 IT-M02-008 被歸類在 Section 4 Redis 測試

**修正建議**:
- IT-M02-007 和 IT-M02-008 應該被歸類在 IT 還是 Redis 測試需要澄清
- 建議統一分類標準，或分開計算

#### A.3 M12 API E2E 數量不一致

**問題位置**: TC_M12_Pricing.md Section 3 (API E2E Tests)

| TC ID | 優先級 | Plan Section 8 | Plan T-M12-05/06 |
|-------|--------|---------------|-------------------|
| API-M12-001 | P0 | API E2E P0=2 | 延後 |
| API-M12-002 | P1 | API E2E P1=3 | 延後 |
| API-M12-003 | P1 | API E2E P1=3 | 延後 |
| API-M12-004 | P0 | API E2E P0=2 | 延後 |
| API-M12-005 | P0 | API E2E P0=2 | 延後 |
| API-M12-006 | P1 | API E2E P1=3 | 延後 |
| API-M12-007 | P1 | API E2E P1=3 | 延後 |
| API-M12-008 | P0 | API E2E P0=2 | 延後 |

**驗證結果**:
- TC_M12_Pricing.md 有 8 個 API E2E (API-M12-001 ~ API-M12-008)
- Plan Section 8 說 "M12 Pricing API E2E: 6 個 (延後)"
- Plan T-M12-05 和 T-M12-06 沒有涵蓋 API E2E

**發現的不一致**:
1. TC 文件有 8 個 API E2E，Plan 說 6 個
2. API-M12-004, API-M12-005, API-M12-008 是 P0 等級，共 3 個 P0 API E2E
3. Plan Section 8 說 "API E2E P0=2"，但應該是 3 個

#### A.4 DoD 中 M12 IT P0 數量不一致

**問題位置**: Sprint 6 Plan DoD Section 9, 第 460 行

```
- [ ] **M12 IT P0 測試通過率 100%** (2/2 TC) ✅ 修正：原 3/3 → 2/2
```

**驗證結果**:
- TC_M12_Pricing.md Section 2.2 有 3 個 P0 等級的 IT:
  - IT-M12-006: 價格計算-週末+早鳥 (P0)
  - IT-M12-007: 價格計算-長住折扣套用 (P0)
  - IT-M12-008: 手動覆蓋-優先於規則 (P0)
- IT-M12-001 和 IT-M12-002 是 P0 等級（Section 2.1）
- 所以 M12 IT P0 實際有 5 個，不是 2 個

**建議 DoD 修正為**:
```
- [ ] **M12 IT P0 測試通過率 100%** (5/5 TC)
```

#### A.5 DoD 中 M02 IT+API P0 數量不一致

**問題位置**: Sprint 6 Plan DoD Section 9, 第 458 行

```
- [ ] **M02 Backend IT+API P0 測試通過率 100%** (9/9 TC) ✅ 修正：M02 IT P0=3 + API E2E P0=6 = 9 P0
```

**驗證結果**:
- TC_M02_Room.md Section 2.1: IT P0=2 (IT-M02-001, IT-M02-002)
- TC_M02_Room.md Section 3.1+3.2: API E2E P0=2 (API-M02-001, API-M02-002)
- TC_M02_Room.md Section 3.3: API E2E P0=2 (API-M02-007, API-M02-008)
- TC_M02_Room.md Section 4: Redis LOCK P0=4 (LOCK-001~LOCK-004)

**問題**:
- 如果計入 Redis LOCK，M02 IT+API P0 = 2 + 4 + 2 = 8 P0（不計 API-M02-003, API-M02-009）
- 如果不計入 Redis LOCK，M02 IT+API P0 = 2 + 4 = 6 P0
- DoD 說 9 P0，需要澄清計算基準

**建議**:
- 明確 DoD 中 "IT+API P0" 的定義範圍
- 是否包含 Redis LOCK 測試？

#### A.6 M12 UT 數量實際為 9 個但有額外 BOUND-* 測試

**問題位置**: TC_M12_Pricing.md Section 4.2

| TC ID | 優先級 | Plan T-M12-05 |
|-------|--------|---------------|
| UT-M12-001 | P0 | ✅ 9 個 UT |
| UT-M12-002 | P0 | ✅ |
| UT-M12-003 | P0 | ✅ |
| UT-M12-004 | P0 | ✅ |
| UT-M12-005 | P1 | ✅ |
| UT-M12-006 | P1 | ✅ |
| UT-M12-007 | P2 | ✅ |
| UT-M12-008 | P0 | ✅ |
| UT-M12-009 | P1 | ✅ |
| BOUND-001 | P1 | ❌ 不在 T-M12-05 |
| BOUND-002 | P1 | ❌ 不在 T-M12-05 |
| BOUND-003 | P2 | ❌ 不在 T-M12-05 |
| BOUND-004 | P1 | ❌ 不在 T-M12-05 |

**驗證結果**:
- T-M12-05 只涵蓋 Section 1 的 9 個 UT (UT-M12-001 ~ UT-M12-009)
- Section 4.2 的 BOUND-* 邊界條件測試（4 個）沒有被納入 Sprint 6 計劃
- 這導致價格計算邊界條件沒有被完整測試

**修正建議**:
- 要麼將 BOUND-* 加入 T-M12-05
- 要麼在 Plan 中明確說明 BOUND-* 延後至未來 Sprint

#### A.7 M06 AT 案例數量 T-DEF-001-05 列表不完整

**問題位置**: Sprint 6 Plan T-DEF-001-05, 第 213-226 行

Plan T-DEF-001-05 說「對應 AT 案例 (共 13 個 P0)」，但列表只有 11 個：

1. AT-M06-001-P0-01 ✅
2. AT-M06-001-P0-04 ❌ (不在列表)
3. AT-M06-001-P0-05 ✅
4. AT-M06-001-P0-06 ✅
5. AT-M06-001-P0-07 ✅
6. AT-M06-001-P0-09 ✅
7. AT-M06-001-NFR-01 ✅
8. AT-M06-002-P0-03 ✅
9. AT-M06-003-P0-01 ✅
10. AT-M06-003-P0-03 ❌ (不在列表)
11. AT-M06-ISOLATION-01 ✅

**缺失的 AT**:
- AT-M06-001-P0-04: 預訂資訊完整性 (P0)
- AT-M06-003-P0-03: 取消後日期格釋放 (P0)

**另外**，TC_M06_Booking.md 還有這些 P0 AT 不在 T-DEF-001-05 列表：
- AT-M06-001-P0-02 (IT)
- AT-M06-001-P0-03 (UT)
- AT-M06-001-P0-08 (IT)
- AT-M06-002-P0-01 (IT)
- AT-M06-002-P0-02 (UT)
- AT-M06-003-P0-02 (IT)
- AT-M06-ISOLATION-02 (P0)
- AT-M06-NFR-01 (NFR)

**建議**:
- 明確 T-DEF-001-05 執行的 AT 範圍（是 E2E 還是包含 IT/UT）
- 補齊列表中缺失的 AT

#### A.8 DoD 中 Booking E2E AT 數量不一致

**問題位置**: Sprint 6 Plan DoD Section 9, 第 457 行

```
- [ ] **Booking E2E P0 測試通過率 100%** (11/11 AT 案例)
```

**驗證結果**:
- T-DEF-001-05 說執行 13 個 AT
- v1.4 QA 審查記錄說「補齊 T-DEF-001-05 AT 案例 (11→13)」
- 但 DoD 還是 11/11

**建議修正為**:
```
- [ ] **Booking E2E P0 測試通過率 100%** (13/13 AT 案例)
```
或根據實際執行情況調整。

---

### B. 依賴關係驗證

#### B.1 依賴關係分析

| 依賴關係 | 驗證結果 | 說明 |
|----------|----------|------|
| T-DEF-001-01 → T-DEF-001-02 | ✅ 正確 | 統一端點實作後才能測試 Feature Toggle |
| T-DEF-001-03 → T-DEF-001-05 | ✅ 正確 | 測試資料就緒後才能執行 Booking E2E |
| T-M12-01 → T-M12-04 | ✅ 正確 | Backend 端點就緒後 Frontend 才能串接 |
| T-M12-01/02/03 → T-M12-05/06 | ✅ 正確 | Backend 重構完成後才能執行 UT/IT |

#### B.2 循環依賴檢查

**檢查結果**: ✅ 無循環依賴

所有依賴關係都是單向的，沒有 A→B→C→A 的循環。

#### B.3 依賴完整性

**發現**: Section 7 的依賴關係是完整的，但缺少以下隱含依賴：

| 隱含依賴 | 說明 |
|----------|------|
| T-M01-03 → T-DEF-001-01/02 | M01 IT 測試依賴統一端點實作 |
| T-M02-03 → T-DEF-001-01/02 | M02 IT 測試依賴統一端點實作 |

**建議**: 在 Section 7 補充這些依賴關係。

---

### C. Story 完整性驗證

#### C.1 US vs TC 映射檢查

| US ID | 對應 TC | 驗證結果 |
|-------|---------|----------|
| US-DEF-001 | - | ✅ 有對應任務 T-DEF-001-01 |
| US-DEF-002 | IT-M02-002, IT-M12-002 | ✅ 有對應 TC |
| US-DEF-003 | - | ✅ 有對應任務 T-DEF-001-03 |
| US-DEF-004 | IT-M02-002, IT-M12-002 | ✅ 有對應 TC |
| FE-M01-001 | - | ✅ 有對應任務 T-M01-01 |
| FE-M01-002 | - | ✅ 有對應任務 T-M01-02 |
| BE-M01-001 | IT-M01-001~003 | ✅ 有對應 TC |
| BE-M01-002 | API-M01-001~003 | ✅ 有對應 TC |
| BE-M12-001 | API-M12-001~003 | ⚠️ API-M12-002/003 是 P1 |
| BE-M12-004 | UT-M12-001~009 | ✅ 有對應 TC |

#### C.2 SP 估算合理性

| Story | 規劃 SP | 實際複雜度 | 評估 |
|-------|---------|------------|------|
| DEF-001 環境建設 | 4 SP | 高（多系統整合） | ✅ 合理 |
| M01/M02 Frontend | 6 SP | 中（CRUD 頁面） | ✅ 合理 |
| M01/M02 Backend API 測試 | 6 SP | 中（IT + API E2E） | ✅ 合理 |
| M12 動態定價 | 8 SP | 高（計算邏輯複雜） | ✅ 合理 |

**發現**: 所有 SP 估算都在合理範圍內，沒有明顯低估或高估。

---

### D. 風險評估驗證

#### D.1 風險覆蓋檢查

| 識別的風險 | 影響 | Plan Section 6 | 對策 | 評估 |
|------------|------|---------------|------|------|
| 測試環境不穩定 | 🟡 中 | ✅ 有 | Buffer 2 SP | ✅ 覆蓋 |
| Frontend 表單複雜度過高 | 🟡 中 | ✅ 有 | MVP 版本 | ✅ 覆蓋 |
| M12 價格計算邏輯變更 | 🟢 低 | ✅ 有 | 已與 BA 確認 | ✅ 覆蓋 |
| API 路徑重構影響現有呼叫 | 🟡 中 | ✅ 有 | 安排 Sprint 開始時執行 | ✅ 覆蓋 |
| 測試案例數量過多 (48 個 TC) | 🔴 高 | ✅ 有 | 優先執行 P0 | ✅ 覆蓋 |

#### D.2 建議新增的風險

| 風險 | 等級 | 建議對策 |
|------|------|----------|
| TC 數量持續變動導致規劃失準 | 🟡 中 | 每次 QA 審查後更新 Plan TC 數量 |
| BOUND-* 邊界條件測試缺失 | 🟡 中 | 評估是否需要加入 Sprint 6 |
| M06 AT 執行範圍不明確 | 🟡 中 | 明確 T-DEF-001-05 執行的 AT 類型 |

---

### E. 文件一致性驗證

#### E.1 Sprint 6 Plan 內部一致性

| 檢查項目 | 位置 | 發現的問題 |
|----------|------|------------|
| TC 數量 | Section 4 vs Section 8 | Section 4 說 48 個 TC，Section 8 說 67 個 TC |
| M12 IT P0 數量 | DoD vs TC 文件 | DoD 說 2/2，TC 文件有 5 個 IT P0 |
| M02 IT+API P0 數量 | DoD vs TC 文件 | DoD 說 9/9，實際需要確認計算基準 |
| Booking E2E AT 數量 | DoD vs T-DEF-001-05 | DoD 說 11/11，T-DEF-001-05 說 13 個 |
| M12 API E2E 數量 | Section 8 vs TC 文件 | Section 8 說 6 個延後，TC 文件有 8 個 |

#### E.2 跨文件一致性

| 文件對 | 一致性 | 問題描述 |
|--------|--------|----------|
| Sprint 6 Plan vs TC_M01_Product.md | ✅ 通過 | M01 TC 數量一致 |
| Sprint 6 Plan vs TC_M02_Room.md | ⚠️ 部分不一致 | IT 數量差 1，歸類方式不同 |
| Sprint 6 Plan vs TC_M06_Booking.md | ❌ 不一致 | AT 數量和範圍不一致 |
| Sprint 6 Plan vs TC_M12_Pricing.md | ❌ 不一致 | API E2E 數量差 2，IT P0 數量差 3 |
| Sprint 6 Plan vs DEFERRED_ITEMS_TRACKER.md | ✅ 通過 | DEF-001 狀態一致 |

---

## 發現的問題

### 🔴 嚴重問題 (Must Fix)

1. **DoD 中 M12 IT P0 數量錯誤**
   - 位置: Sprint 6 Plan DoD Section 9, 第 460 行
   - 問題: DoD 說 2/2 TC，但 TC_M12_Pricing.md 有 5 個 IT P0
   - 影響: DoD 無法作為有效的驗收標準
   - 建議修正: 改為 "(5/5 TC)" 或 "(3/5 TC，IT-M12-006/007/008)"

2. **DoD 中 Booking E2E AT 數量過時**
   - 位置: Sprint 6 Plan DoD Section 9, 第 457 行
   - 問題: DoD 說 11/11，但 T-DEF-001-05 說執行 13 個 AT
   - 影響: 團隊不知道實際目標是 11 個還是 13 個
   - 建議修正: 改為 "(13/13 AT 案例)" 並更新 T-DEF-001-05 列表

3. **M12 API E2E 數量不一致**
   - 位置: Sprint 6 Plan Section 8 vs TC_M12_Pricing.md
   - 問題: Plan 說 6 個 API E2E 延後，TC 文件有 8 個
   - 影響: 測試覆蓋範圍不清晰
   - 建議: 明確 8 個 API E2E 中哪些是 Sprint 6 範圍

4. **T-DEF-001-05 AT 列表不完整**
   - 位置: Sprint 6 Plan T-DEF-001-05
   - 問題: 說執行 13 個 AT，但列表只有 11 個
   - 缺失: AT-M06-001-P0-04, AT-M06-003-P0-03
   - 建議: 補齊列表或說明篩選標準

### 🟡 中等問題 (Should Fix)

1. **M02 IT+API P0 計算基準不明**
   - 位置: Sprint 6 Plan DoD Section 9
   - 問題: DoD 說 9 P0，但計算方式不明確（是否含 Redis LOCK）
   - 建議: 明確 "IT+API P0" 的定義，或分開計算

2. **BOUND-* 邊界條件測試未納入 Sprint 6**
   - 位置: TC_M12_Pricing.md Section 4.2
   - 問題: 4 個 BOUND-* 測試不在 T-M12-05 範圍內
   - 建議: 評估是否需要加入或說明延後原因

3. **Section 7 缺少隱含依賴**
   - 位置: Sprint 6 Plan Section 7
   - 問題: T-M01-03/T-M02-03 依賴 T-DEF-001-01/02 未列入
   - 建議: 補充這些依賴關係

4. **TC 數量報告不一致**
   - 位置: Sprint 6 Plan Section 4 vs Section 8
   - 問題: Section 4 說 48 個 TC，Section 8 說 67 個 TC
   - 建議: 統一 TC 數量報告標準

### 🟢 建議 (Nice to Have)

1. **新增風險：TC 數量持續變動**
   - 建議在 Section 6 新增此風險

2. **新增風險：M06 AT 執行範圍不明確**
   - 建議在 Section 6 新增此風險

3. **統一 TC 分類標準**
   - M02 IT 和 Redis LOCK 測試的歸類需要統一

4. **建議在 TC 文件中標記 Sprint 6 執行範圍**
   - 在 TC_Mxx 文件抬頭新增 "Sprint 6 範圍" 標記

---

## 總結

### 驗證通過

**No** - Sprint 6 Plan v1.4 有多處數據不一致，需要修正後才能作為有效的執行依據。

### 剩餘風險

1. **數據不一致風險**: 多處數字不一致可能導致團隊溝通混亂和預期落差
2. **測試覆蓋缺口風險**: BOUND-* 邊界條件測試未被涵蓋，可能導致價格計算 bug
3. **範圍不明確風險**: M06 AT 執行範圍（E2E vs IT/UT）和 M12 API E2E 範圍不清楚

### 後續行動建議

| 優先級 | 行動 | 負責人 |
|--------|------|--------|
| 🔴 Must Fix | 修正 DoD 中 M12 IT P0 數量 (2/2 → 5/5 或 3/5) | PM/QA |
| 🔴 Must Fix | 修正 DoD 中 Booking E2E AT 數量 (11/11 → 13/13) | PM/QA |
| 🔴 Must Fix | 補齊 T-DEF-001-05 AT 列表或說明篩選標準 | QA |
| 🔴 Must Fix | 澄清 M12 API E2E 數量 (6 個 vs 8 個) | QA |
| 🟡 Should Fix | 明確 M02 IT+API P0 計算基準 | QA |
| 🟡 Should Fix | 補充 Section 7 隱含依賴 | SA |
| 🟡 Should Fix | 評估 BOUND-* 是否需要加入 Sprint 6 | QA |
| 🟢 Nice to Have | 新增 TC 數量變動風險至 Section 6 | PM |

---

**文件版本**: v2.0
**驗證員**: QA (Quincy)
**驗證日期**: 2026-04-29
**下次審查**: 修正後重新驗證
