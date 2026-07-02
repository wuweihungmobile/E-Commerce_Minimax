# CJK 字體技術評估與決策 / CJK Font Assessment & Decision

> **建立日期**: 2026-07-02
> **Sprint**: Sprint 41 US-006（DEF-021，續延多 Sprint 後之評估型交付）
> **狀態**: 評估完成 → **決策建議：維持系統字體堆疊（accepted fallback），@font-face 路徑記錄為選配未來任務**
> **維護者**: SD Marcus + Dev David + Claude Code

---

## 1. 背景（DEF-021）

專案為繁體中文賣場，理想是全站以一致的品牌 CJK 字體（原規劃 **Noto Sans TC**）呈現。Sprint 35 曾嘗試以 `next/font` 自 host Inter + Noto Sans TC，但**建置失敗**，退回系統字體堆疊，並登記為 DEF-021（P3）續延至今。

---

## 2. 現況（技術盤點）

| 項目 | 現況 | 位置 |
|------|------|------|
| Latin 字體 | Inter，經 `next/font/google` 自 host（`subsets: ["latin"]`）| `frontend/src/app/layout.tsx:2,10-14,32` |
| CJK 字體 | **系統字體堆疊**（未 ship 字體檔）| `frontend/src/app/globals.css:161-163` |
| 字體堆疊 | `var(--font-inter), ui-sans-serif, system-ui, "PingFang TC", "Noto Sans TC", "Microsoft JhengHei", "Heiti TC", sans-serif` | `globals.css:161-163` |
| 套用 | `body { font-family: var(--font-sans); }` | `globals.css:188-192` |
| 自 host 字體檔 | **無**（`frontend/public/` 只有 svg，無 woff2/ttf/otf）| `frontend/public/` |
| 建置工具 | **Turbopack**（Next 16.2.2 預設，dev + build）| `frontend/package.json`、`next.config.ts` |
| CSP | **無**（全 repo 無 Content-Security-Policy / font-src 限制）| （grep 無結果）|

**目前 CJK 呈現效果**：
- macOS / iOS：`PingFang TC`（系統內建，效果佳）
- Windows：`Microsoft JhengHei`（系統內建，效果佳）
- 其他 / 無上述字體之裝置：退回 `sans-serif`（效果不一致）
- `Noto Sans TC` 僅在使用者「本機已安裝」時生效（未隨站台 ship）

---

## 3. 根本限制（為何 next/font 走不通）

- `next/font`（Google / local）為 CJK 產生**大量 `unicode-range` 子集**的 `@font-face`（CJK 上萬字元需切成數百段 subset）。
- **Turbopack 無法解析**這種大量 unicode-range 子集 → **建置失敗**（`SPRINT_35_RETRO.md:47`、`layout.tsx:7-9` 註解已載明）。
- 另專案 **build 為離線執行**（`e697dbe` commit body：「離線 npm run build 通過」）→ 不能依賴 `next/font/google` 的 runtime CDN。

> 關鍵：限制**專屬於 `next/font` 產生的 subset 機制**，**不是** CSP、也不是靜態 `@font-face` 本身。

---

## 4. 可行路徑評估

### 選項 A：維持系統字體堆疊（現況）✅ 建議
- **作法**：不動。CJK 由使用者 OS 內建字體呈現。
- **優點**：零成本、零 repo 肥大、零建置風險；主流平台（Mac/Win）效果已佳。
- **缺點**：跨裝置字體不完全一致；無法保證品牌字體。
- **風險**：無。
- **狀態**：已是現行 fallback，Sprint 35 Retro 已評為「低嚴重度可接受」。

### 選項 B：`@font-face` 靜態自 host 子集 woff2（繞過 next/font）
- **作法**：以字型子集工具（`fonttools` / `glyphhanger`）將 **Noto Sans TC**（SIL OFL 授權，可自由嵌入/子集）子集為 woff2，放 `frontend/public/fonts/`，在 `globals.css` 手寫 `@font-face` 並把 `'Noto Sans TC'` 提到堆疊最前。
- **可行性**：**技術上可行**——**無 CSP 阻擋**、`public/` 目前無字體檔（乾淨）、靜態 `@font-face` **不經 next/font subset 機制**（故不觸發 Turbopack 失敗）、資產 commit 進 repo 故**離線建置 OK**。此為 Sprint 35「已知失敗」以外的**未試路徑**。
- **成本 / 代價**：
  1. 需 source 授權字體（Noto Sans TC OFL，免費）並建立子集工具鏈
  2. CJK 即使子集化仍偏大（常用字子集 **~1-3MB woff2**）→ **repo 二進位肥大 + 首屏字體下載**
  3. 需驗證 Turbopack 對靜態 `@font-face` + 大 woff2 的處理（未試，需 spike）
  4. 子集需涵蓋全站實際用字（動態內容如商品名可能超出子集 → 缺字 fallback）
- **風險**：中（未試路徑需 spike；字體檔維護成本）。

### 選項 C：`next/font/local` 單一 woff2（不切 unicode-range）
- 理論上 `next/font/local` 指向單一大 woff2 可能避開自動 subset，但仍走 next/font 管線，**Turbopack 相容性未經證實**，且同樣有 1-3MB 肥大問題 → 不優於選項 B，不建議。

---

## 5. 決策建議

> **建議採選項 A（維持系統字體堆疊），並將 DEF-021 結案為「accepted fallback」；選項 B 記錄為選配未來任務（品牌一致性成為優先時再啟）。**

**理由**：
1. **P3 且已有可用 fallback**：主流平台（Mac/Win）CJK 呈現已佳，缺陷嚴重度低。
2. **成本 / 效益不成比例**：為 P3 外觀一致性引入 1-3MB 二進位資產 + 子集工具鏈 + Turbopack spike，違反最小化原則（Rule 2）。
3. **repo 衛生**：避免大型二進位進 git（膨脹 clone / CI）。
4. **未關死**：選項 B 為明確、技術可行的升級路徑，已完整記錄，未來品牌需求出現時可直接執行。

**若使用者選擇投資品牌一致性（選項 B）**，建議獨立開一個正式 task（非 P3 順帶），內容：spike Turbopack + 子集工具鏈 → 決定子集字元集 → 加入 `public/fonts/` + `@font-face` → 驗證建置與首屏效能 → 更新字體堆疊。

---

## 6. 🔴 待使用者拍板

- **預設（建議）**：採選項 A，DEF-021 結案為 accepted fallback。
- **替代**：採選項 B，開正式 task 投資自 host Noto Sans TC（接受 ~1-3MB 資產與 spike 成本）。

> 依 Sprint 41 計劃 AC-006-3：P3 下**不逕行**加入大型字體二進位，需使用者明確指示才執行選項 B。

---

**文件版本**: v1.0
**建立者**: SD Marcus + Dev David + Claude Code
**基於**: AISDLC v0.09；S41 Explore 探勘（字體現況）
