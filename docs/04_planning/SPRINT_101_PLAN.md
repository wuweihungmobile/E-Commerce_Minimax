# Sprint 101 Plan — FREE_SHIPPING 免運券落地 + 購物車運費預覽（DEF-045）

**Sprint**: Sprint 101
**日期**: 2026-09-01
**AI 編號**: AI-2435
**主題**: 修復 Sprint 100 掃描順帶發現的 DEF-045——`FREE_SHIPPING` 折扣型別在全系統無任何對應處理，店家發此型別的券、買家套用後折扣為 0 且運費照收。連帶接上購物車的運費／折扣顯示（原 `GET /v2/cart` 連 `appliedPromoCode` 都不回傳）。

---

## 1. 缺口盤點結果

### 起點

本輪不是新一輪 PRD 全文掃描，而是承接 Sprint 100 已記錄、且使用者裁定優先處理的延後項目 **DEF-045**。

### 確認的缺口

| # | 缺口 | 證據 | 嚴重性 |
|---|------|------|--------|
| 1 | **免運券完全不生效** | `PromoService.computeDiscount` 對 `FREE_SHIPPING` 直接回傳 `BigDecimal.ZERO`，註明「免運費由物流模組處理」；但全庫 grep 確認物流模組（`ShippingTemplateService`）**沒有任何 `FREE_SHIPPING` 相關邏輯**。main 程式碼僅 3 處提及此值：enum 定義、這個回 ZERO 的 case、一行 DTO 註解 | 🔴 買家拿不到已承諾的優惠 |
| 2 | **購物車不回傳促銷資訊** | `CartController.getCart`（`GET /v2/cart`）呼叫的是 `getCart()` 而非 `getCartWithPromo()`——**後者在 main 程式碼是零呼叫死碼**。買家套券後重新整理購物車頁，折扣顯示即消失（券仍在 Redis，Sprint 100 後結帳仍會生效，故非收款錯誤，但顯示與實收對不上） | 🟠 顯示斷鏈 |
| 3 | **購物車從不顯示運費** | `CartDto.CartResponse` 無運費欄位，運費只在 `OrderService` 建單時才算。免運券的折抵若不讓購物車看得到，買家無從判斷券是否生效 | 🟠 |

**訊號類型**：缺口 2 再次命中 Sprint 96/97/100 已列為標準檢查項的「**Service 方法存在但全庫零呼叫者**」模式——這是本專案第四次由此訊號直接找到缺口。

### PRD 依據與其不足

PRD §769 的 M04 模組表僅寫「購物車快取、優惠券套用、滿額折扣/**免運計算**、限時搶購邏輯」，`FREE_SHIPPING` 的**計算規則 PRD 全文未定義**（無 Test Case、無後置條件）。故本輪的計算語意屬產品決策，已透過 AskUserQuestion 交由使用者拍板，未自行假設。

---

## 2. 使用者決策（AskUserQuestion 拍板，2026-09-01）

| 決策點 | 選項 | 裁定 |
|--------|------|------|
| 免運券折抵金額算法 | 全額折抵運費 / 依 `discountValue` 設上限 / 本輪改推 DEF-046 | **全額折抵運費**；`maxDiscountAmount` 若有設仍為上限（店家仍可發「最多折抵 60 元運費」的券） |
| 購物車是否同步顯示 | 只修結帳 / 購物車一併預覽運費與免運 | **購物車一併預覽**（顯示與收款一致，代價是動 API 契約與前端） |
| Sprint 93~100 RETRO 文件形式 | 承認新慣例 / 回填 8 份 / 從 101 起恢復 | **正式承認新慣例**，見 [SPRINT_ARTIFACT_CONVENTION.md](../05_development/SPRINT_ARTIFACT_CONVENTION.md) |

**與滿額免運的疊加問題**：不需要另做排除邏輯。店家若已設 `FREE_THRESHOLD` 模板且訂單達門檻，運費本身即為 0，免運券自然算出 0 折扣。

---

## 3. 實作內容

### 服務層

- **`PromoService.computeDiscount`** 改為單一 3 參數簽章 `(promo, itemsTotal, shippingFee)`，**刻意不保留原本的 2 參數多載**：`FREE_SHIPPING` 的折扣基數是運費而非商品小計，若留下不需傳運費的多載，呼叫端會在毫無徵兆的情況下拿到 0 折扣——DEF-045 正是這樣產生的。移除多載後編譯器一次列出全部 4 個呼叫點與 4 個測試檔，屬刻意的「大聲失敗」設計。
  - 最低消費門檻（`minPurchaseAmount`）一律以商品小計判斷，不含運費。
  - 折扣型別分派抽為私有 `rawDiscountByType`：內嵌會讓 `computeDiscount` 的 NPath 複雜度衝到 288（checkstyle 上限 200），與 Sprint 100 踩到的是同一個陷阱。
- **`OrderService.applyPromoDiscount`** 將已算好的 `shippingFee` 一併傳入折扣計算。
- **`RedisCartService`**：
  - 新增私有 `previewShippingFee`，基數**只取 PRODUCT 項目小計**，與 `createOrderFromCart` 一致（後者只結 PRODUCT、ROOM 另行結帳）；純 ROOM 或空購物車直接回 0，否則 FIXED 型運費模板會對訂房購物車顯示一筆不存在的運費。
  - `getCartWithPromo` 一律回填 `shippingFee`／`discountAmount`／`finalAmount`（與是否套券無關）。
  - `applyPromoCode` 回應加入 `shippingFee`，`finalAmount` 改為含運費。

### API 契約

- `CartDto.CartResponse` 新增 `shippingFee`；`finalAmount` 語意變更為 **`totalAmount + shippingFee - discountAmount`**（此前不含運費）。
- `CartDto.ApplyPromoResponse` 新增 `shippingFee`，`finalAmount` 同上。
- **`GET /v2/cart` 改呼叫 `getCartWithPromo`**（修復缺口 2）。

### 前端

`cart/page.tsx` 訂單摘要新增「運費」列（0 時顯示「免運」），總金額改為一律取 `finalAmount`；移除券時重算 `finalAmount = totalAmount + shippingFee`，避免畫面退回不含運費的小計。

---

## 4. 測試

### `PromoServiceTest`（+6，共 23）

新增 6 個 `FREE_SHIPPING` 案例（全額折抵運費、折抵只跟運費走／商品再貴不多折、店家已滿額免運時回 0、受 `maxDiscountAmount` 上限限制、未達最低消費門檻回 0、運費為 null 不得 NPE）與 1 個 `PERCENTAGE` 案例（證明運費**不**參與商品折扣基數），並移除 1 個已被取代的舊斷言（`freeShipping_returnsZero`），故淨 +6。

**紅燈驗證（實際執行）**：暫時把 `case FREE_SHIPPING` 改回舊的 `BigDecimal.ZERO` 重跑，3 個行為型斷言確實失敗（`expected: 120.00 / 60.00 / 60.00`，實得 0），確認測試真的綁在新語意上而非空過。其餘 3 個為守衛型斷言（新舊皆應為 0），不在紅燈範圍——如實記錄。

### `OrderPromoCodeTest`（+2，共 16）

- `passesShippingFeeToDiscountCalculation`：`verify` 結帳確實把運費傳給 `computeDiscount`——這正是 DEF-045 的失效點，缺了它免運券就會靜默回 0。
- `freeShippingCouponWaivesShippingFee`：小計 200 + 運費 60 - 折抵 60 = 實收 200（修復前 260）。

### `RedisCartServiceTest`（+3，共 23）

PRODUCT 購物車回填運費、純 ROOM 購物車運費為 0 且**不呼叫**運費模板、套免運券後折抵等於運費。

### `M11PromoCheckoutIntegrationTest`（+1，共 2；真實 DB + 真實 Redis + 真實 JWT）

`freeShippingPromoWaivesShippingFee` 走完整迴路：真實寫入 FIXED 運費模板（60）與 `FREE_SHIPPING` 券 → `GET /v2/cart` 斷言 `shippingFee=60`、`finalAmount=260` → 套券斷言折抵 60、`finalAmount=200` → 下單斷言 `shippingFee=60`、`discountAmount=60`、`totalAmount=200`。刻意不 mock 任何一層，延續 Sprint 100 的作法。

---

## 5. 驗證結果

### 過程中遇到的環境陷阱（可複用）

第一次全量回歸出現 `M11ShippingFeeIntegrationTest` 3 個 Error：`Unresolved compilation problem: TestSecurityContextHelper cannot be resolved`。

**這不是回歸**——該檔本輪完全未修改，`TestSecurityContextHelper` 也確實存在且簽章相符。根因是 **VS Code Java Language Server（Eclipse JDT/ECJ）把一個編譯失敗的 `.class` 寫進 `target/test-classes/`**，時間戳晚於 Maven 的編譯產物。判別依據：

1. `Unresolved compilation problem` 是 ECJ 的錯誤格式，**javac 從不產生此字串**——出現它就代表該 class 是 IDE 寫的。
2. `grep -arl "Unresolved compilation" backend/target/test-classes/ | wc -l` 全樹只有這 1 個檔中鏢。

刪除該 `.class` 後以 Maven 重編，3/3 通過。因第一次回歸是在污染狀態下跑的，結果不予採信，改以 `mvn -o clean verify -Pintegration-test` 重跑。

**另附一個 zsh 陷阱**：`rm -f a.class a$*.class` 在 `a$*.class` 無匹配時，zsh 會**中止整個命令**（連能匹配的第一個檔也不會刪），造成「以為刪了其實沒刪」。刪除編譯產物請改用 `find ... -delete`。



- 後端全量回歸 `mvn -o clean verify -Pintegration-test`：✅ **1460 tests 0 fail**（單元 1068 + 整合 392）
- 本輪新增測試淨 **+12**（以 `@Test` 實際計數核對：`PromoServiceTest` +6、`OrderPromoCodeTest` +2、`RedisCartServiceTest` +3、`M11PromoCheckoutIntegrationTest` +1）
- Checkstyle：0 violations（過程中修正一次 NPath 超標，抽出 `rawDiscountByType`）
- 前端 `tsc --noEmit`：0 errors；`eslint`：0 errors（94 warnings 皆為既有 anonymous default export，與本輪無關）
- `make validate-release`：⏳ commit 後執行（push gate；結果補記於 RELEASE_TRACKER）

---

## 6. 範圍外（延後）

- **DEF-046 併發額度競態**、**DEF-047 ROOM 訂單套券**：維持延後，本輪未動。
- **混合購物車（PRODUCT + ROOM）的折扣基數不一致**（新記錄為 **DEF-048**）：購物車顯示用的折扣基數是 `cart.getTotalAmount()`（含 ROOM 項目），而結帳的基數只有 PRODUCT 小計。此為 Sprint 100 之前即存在的行為，與 `FREE_SHIPPING` 正交，本輪刻意不順手改動（Rule 3 精準改動），改為記錄。
- **PRODUCT 結帳頁（`checkout/product/page.tsx`）未顯示運費與折扣**：該頁自行以 `items` 加總商品小計，並標示「運費將於建立訂單時計算」，不使用 `finalAmount`（故本輪的 `finalAmount` 語意變更對它無影響，也不會重複計運費）。使用者本輪裁定的顯示範圍是**購物車**，未擴及結帳頁；該頁文案誠實、非誤導，故不順手改動。資料現已由同一支 `GET /v2/cart` 提供，未來要補很便宜。
- **`ShippingTemplateService.calculateFeeForTenant` 取 `templates.get(0)`**：租戶有多個運費模板時固定取第一個，非本輪缺口，未動。

---

## 7. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S98 | 8 |
| S99 | 6 |
| S100 | 8 |
| **S101** | **5**（1 個折扣型別落地 + 購物車運費預覽前後端 + 12 個新測試） |

---

## 8. 下一步 / Action Items

| # | 項目 | 說明 |
|---|------|------|
| 1 | DEF-046（併發額度競態） | Sprint 100 起連續兩輪列為候選。可複用 `RateLimitFilter`（Sprint 93）的 Redis Lua 原子操作，或改用條件式 UPDATE |
| 2 | DEF-047（ROOM 訂單套券，PRD US-010） | 規模較大：需新增 `POST /v2/orders` ROOM 分支的促銷碼契約 + 訂房結帳前端 |
| 3 | DEF-048（混合購物車折扣基數） | 本輪新記錄，需先確認產品期望（券是否該折抵 ROOM 項目） |
| 4 | 第九輪 PRD 全文掃描 | 若上述皆不排入，回到 Sprint 93-100 的方法論繼續找未追蹤缺口 |

---

**文件版本**: v1.0｜**建立者**: Claude Code（AISDLC v0.09 Sprint Planning）｜**基於**: PRD v1.0 Final §769（M04）+ DEF-045
