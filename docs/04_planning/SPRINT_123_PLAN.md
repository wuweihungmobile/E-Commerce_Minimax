# Sprint 123 Plan — DEF-068：schema 文件涵蓋率缺口

**Sprint**: Sprint 123
**日期**: 2026-09-04
**主題**: 待拍板延後項目中，使用者授權依序處理的第一項，3 SP。

---

## 1. 缺陷

`check_schema_doc.py` 是逐一走訪 `SRD_Database_Schema.md` §2 裡**已經寫出來的**表區塊去
比對，`stock_movements.reference_number`（Sprint 117）就是靠 PRD §8.2 那一半才被攔下——
`stock_movements` 根本不在 SRD 的 14 張表裡。「✅ 文件無漂移」的真正意思是「有寫的那些表
沒漂移」，不是「文件是完整的」。這正是 DEF-062（SRD 100 個 Sprint 沒人發現逐欄都是錯的）
會在未被文件化的表上重演的路徑。

## 2. 決策：拍板選 (b) + (c) 混合，放棄純 (a)

原記錄列出三個選項：(a) 補齊全部資料表的 SRD DDL；(b) 讓守門對「實作有、文件沒有」的表
主動報錯；(c) 明確定義 SRD 範圍並寫進文件。

**查證量測**（起乾淨 `postgres:18-alpine` 套完 75 個 Flyway 遷移，查 `information_schema`）：
實作共 **65 張表**，SRD §2 有 DDL 的 14 張 + PRD §8.2 的 15 張，扣除重疊後**只覆蓋 26 張，
39 張完全沒被任何文件提到**。純 (a) 等於要為 39 張表手寫 DDL 分節與中文說明，規模遠超過
本項目原估的 3 SP（且 DEF-068 自己的前置需求欄也寫「需人工審閱分節與說明」），不是能一次
autonomous 完成、又對得起「精準改動」（Rule 2）的做法。

選擇 **(b)＋(c) 混合**：
- 新增 §2.6「已知範圍外資料表」清單（(c)）——不是逐欄 DDL，而是 39 張表依模組分類的
  一行說明，供快速查找，並註明日後要補正式規格時把表名移到 §2 對應小節即可。
- `check_schema_doc.py` 新增涵蓋率守門（(b)）——任何實作表若不在「SRD §2 DDL」∪
  「PRD §8.2」∪「§2.6 清單」任何一處，直接報錯；§2.6 清單裡若列了實作中已不存在的表，
  同樣報錯（防止清單本身漂移）。

這樣「文件沒寫的表，漂移完全不會被發現」的根因被堵住的是**未來**——下一張新表若沒人
把它加進三處之一，`make validate-schema-doc` 會直接失敗，逼開發者做出明確決策，而不是
像過去一樣沒人發現。至於**現有** 39 張表缺 DDL 這件事，選擇誠實記錄為技術債（列在 §2.6），
不假裝已經解決。

## 3. 修法

1. `docs/02_architecture/SRD_Database_Schema.md` — 新增「### 2.6 已知範圍外資料表
   （技術債，DEF-068）」，39 張表依模組分類（會員／購物車／訂房／促銷／金流／物流／
   評論／即時聊天／通知／客服／CMS／知識庫／ERP／稽核），含 2 張標記為遷移殘留備份表
   （`_media_assets_backup`／`listings_tags_backup`，非本次範圍）與 1 張標記為與現行
   `audit_log` 並存、未查證是否重複的 `audit_logs`（同樣非本次範圍，僅存查）。
2. `scripts/lib/check_schema_doc.py` — 新增 `out_of_scope_tables()`（解析 §2.6 清單）與
   `check_coverage()`（比對 `information_schema.tables` 實際表 vs. SRD §2 ∪ PRD §8.2 ∪
   §2.6 清單，雙向報錯：未涵蓋的實作表、以及清單裡已不存在的表），接進 `main()` 既有的
   `problems`／`--write` 報告流程。

## 4. 範圍外

- 39 張表的正式逐欄 DDL 文件化（選項 (a) 的完整版）：規模超出本輪，且需要對每張表的
  角色/欄位語意做人工審閱，留待日後視需要個別搬進 §2。
- `audit_log` 與 `audit_logs` 是否重複：本輪只如實記錄兩表並存的事實，不查證/不合併，
  非 DEF-068 範圍。
- `_media_assets_backup`／`listings_tags_backup` 兩張遷移殘留備份表是否該清除：非
  DEF-068 範圍，僅在 §2.6 註明來源遷移供之後排查。

## 5. 驗證

| # | 內容 | 結果 |
|---|------|------|
| ① | `python3 -m py_compile scripts/lib/check_schema_doc.py` | 編譯成功 |
| ② | `make validate-schema-doc`（正常狀態） | ✅ 通過，含新增的 §2.6 涵蓋率守門 |
| ③ | 負向測試：暫時從 §2.6 清單移除 `addresses` 一列 → 重跑 | 🔴 如預期失敗，訊息點名 `addresses`「實作存在，但未出現在 SRD §2 / PRD §8.2 / §2.6 範圍外清單任何一處（DEF-068 守門）」 |
| ④ | 還原後重跑 | ✅ 恢復通過 |

ℹ️ 本輪未改動任何 `backend/`／`frontend/` 程式碼（純 `docs/`＋`scripts/lib/` Python），
不影響任何 Spring/前端被測程式碼行為，pre-push 輕量守門依既定規則（見
`docs/08_deployment/LOCAL_CI_VALIDATION.md`）判定為純文件/設定變動而略過；上述 ②-④
才是這次變更實際要驗證的行為，已手動跑過紅燈/綠燈兩態。
