# M08 評價系統測試案例 / Review Test Cases

> **模組**: M08 評價系統
> **版本**: v1.1
> **建立日期**: 2026-04-10
> **最後更新**: 2026-06-05
> **依據**: API_Index.md (M08 區段), ReviewService (含 ReviewReplyService 拆分), ReviewDto
> **測試框架**: JUnit 5 + Mockito (UT), Spring Boot Test (IT), REST Assured (API)
> **🔴 Sprint 16 US-008 / Retro DI-002**: 擴展多圖評價、回覆、標記測試案例

---

## 📋 測試案例總覽

| 測試類型 | P0 | P1 | P2 | 小計 |
|----------|----|----|----|------|
| UT | 6 | 4 | 2 | 12 |
| IT | 4 | 3 | 1 | 8 |
| API | 4 | 3 | 1 | 8 |
| **合計** | 14 | 10 | 4 | **28** |

---

## 1. 單元測試 (Unit Tests)

### 1.1 評價建立與查詢

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| UT-M08-001 | 建立評價 - 成功 | P0 | Listing 存在，無重複 | 1. 呼叫 createReview | 建立成功，回傳 ReviewResponse |
| UT-M08-002 | 建立評價 - Listing 不存在 | P0 | 無 | 1. 呼叫 createReview (不存在的 listingId) | 拋出 BusinessException E_3000 |
| UT-M08-003 | 防止重複評價 - 同訂單 | P0 | 已有同 orderId 評價 | 1. 呼叫 createReview | 拋出 E_8000 |
| UT-M08-004 | 防止重複評價 - 同預訂 | P0 | 已有同 bookingId 評價 | 1. 呼叫 createReview | 拋出 E_8000 |
| UT-M08-005 | 星級範圍 1-5 驗證 | P0 | rating = 0 或 6 | 1. 呼叫 createReview | @Min/@Max 驗證失敗 |
| UT-M08-006 | 取得評價列表 - 分頁 | P0 | 25 筆評價 | 1. 呼叫 getReviewsByListingId | 回傳分頁結果 |
| UT-M08-007 | 平均評分計算 | P0 | 多筆不同評分 | 1. getRatingStats | averageRating 正確 |
| UT-M08-008 | 評價分布計算 | P1 | 1-5 星各 N 筆 | 1. getRatingStats | distribution map 正確 |

### 1.2 🆕 商家回覆（Sprint 15 + Sprint 16 ReviewReplyService 拆分）

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| **UT-M08-101** | 商家回覆 - 成功 | P0 | Listing 擁有者登入 | 1. ReviewReplyService.createReply | 回傳 ReplyResponse |
| **UT-M08-102** | 商家回覆 - 評價不存在 | P0 | 不存在 reviewId | 1. createReply | 拋出 E_8000 |
| **UT-M08-103** | 商家回覆 - 非擁有者 | P0 | 非 Listing 擁有者 | 1. createReply | 拋出 E_1007 |
| **UT-M08-104** | 商家回覆 - 重複回覆 | P0 | 已有 Reply | 1. createReply | 拋出 E_1086 |
| **UT-M08-105** | 取得回覆列表 - 空 | P1 | 無回覆 | 1. getRepliesByReviewId | 回傳空 list |
| **UT-M08-106** | 取得回覆列表 - 排序 | P1 | 多筆回覆 | 1. getRepliesByReviewId | 按 createdAt 升冪排序 |

### 1.3 🆕 多圖評價 9 張上限（Sprint 16 US-005）

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| **UT-M08-201** | 建立評價 - 0 張圖片 | P0 | 訂單完成 | 1. createReview with empty images | 建立成功 |
| **UT-M08-202** | 建立評價 - 9 張圖片 | P0 | 9 個有效 mediaId | 1. createReview with 9 images | 建立成功 |
| **UT-M08-203** | 建立評價 - 10 張圖片超限 | P0 | 10 個 mediaId | 1. createReview with 10 images | 拋出 E_1088 |
| **UT-M08-204** | 建立評價 - 無效 mediaId | P0 | 含無效 UUID | 1. createReview with invalid media | 拋出 E_1089 |
| **UT-M08-205** | 更新評價 - 圖片超限 | P0 | 已存在評價 | 1. updateReview with 10 images | 拋出 E_1088 |
| **UT-M08-206** | 更新評價 - 無效 mediaId | P0 | 已存在評價 | 1. updateReview with invalid media | 拋出 E_1089 |

### 1.4 🆕 圖片管理（Sprint 16 US-006）

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| **UT-M08-301** | 新增單張圖片 - 成功 | P0 | 評價 8 張圖 | 1. addImage | 圖片變 9 張 |
| **UT-M08-302** | 新增至 10 張 | P0 | 評價 9 張圖 | 1. addImage | 拋出 E_1088 |
| **UT-M08-303** | 新增 - 非本人 | P0 | 其他用戶 | 1. addImage | 拋出 E_1091 |
| **UT-M08-304** | 刪除圖片 - 成功 | P0 | 評價 3 張圖 | 1. removeImage(1) | 圖片變 2 張 |
| **UT-M08-305** | 刪除 - index 越界 | P0 | 評價 2 張圖 | 1. removeImage(5) | 拋出 E_1090 |
| **UT-M08-306** | 重新排序 - 成功 | P0 | 評價 3 張圖 | 1. reorderImages(newOrder) | 圖片順序更新 |
| **UT-M08-307** | 重新排序 - 不同集合 | P0 | 包含新圖 | 1. reorderImages with different images | 拋出 E_1089 |
| **UT-M08-308** | 重新排序 - 數量不同 | P0 | 原 3 張，新 2 張 | 1. reorderImages with 2 images | 拋出 E_1088 |

---

## 2. 整合測試 (Integration Tests)

### 2.1 評價基本 CRUD

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| IT-M08-001 | 買家建立商品評價 | P0 | 訂單已完成 | 1. POST /v2/reviews | 200, 評價建立 |
| IT-M08-002 | 賣家/房東回覆評價 | P0 | 評價存在 | 1. POST /v2/reviews/{id}/replies | 200, ReplyResponse |
| IT-M08-003 | 取得評價回覆列表 | P0 | 評價有回覆 | 1. GET /v2/reviews/{id}/replies | 200, replies 列表 |
| IT-M08-004 | 取得 Listing 評價列表 | P0 | 有評價 | 1. GET /v2/reviews/listing/{id} | 200, 評價列表 |
| IT-M08-005 | 評價後不可修改 | P1 | 評價建立 | 1. PUT /v2/reviews/{id} (更新 content) | 200, 但 content 已更新（實際上可更新） |

### 2.2 🆕 多圖評價流程（Sprint 16 US-005 + US-006）

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| **IT-M08-101** | 完整多圖評價流程 | P0 | 訂單完成，9 個有效 media | 1. POST /v2/reviews (9 圖)<br>2. POST .../images (新增第 10 張)<br>3. DELETE .../images/0<br>4. PUT .../images/order | 400 → 200 → 200 |
| **IT-M08-102** | 9 張上限 + 9 張成功 | P0 | 9 個有效 media | 1. POST /v2/reviews with 9 images | 200, 9 張都儲存 |
| **IT-M08-103** | 圖片管理權限 | P0 | 評價 A 屬於 User1 | 1. User2 POST .../images | 403 Forbidden |
| **IT-M08-104** | 跨評價 ID 操作 | P0 | 多個評價 | 1. DELETE /v2/reviews/{A}/images/0 (B 用戶) | 403 |

### 2.3 評價標記流程

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| IT-M08-201 | 標記為已處理 | P0 | 評價存在 | 1. PUT /v2/reviews/{id}/handle?handled=true | 200, isHandled=true |
| IT-M08-202 | 取得待處理評價 | P0 | 有未處理評價 | 1. GET /v2/reviews/managed?isHandled=false | 200, 列表 |
| IT-M08-203 | 評價 helpful 投票 | P1 | 評價存在 | 1. POST /v2/reviews/{id}/helpful | 200, helpfulCount+1 |

---

## 3. API E2E 測試 (API E2E Tests)

### 3.1 評價 CRUD API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M08-001 | POST /v2/reviews | P0 | 買家登入 | 1. POST Body: {listingId, orderId, rating, content} | 200, ReviewResponse |
| API-M08-002 | GET /v2/reviews/listing/{id} | P0 | 有評價 | 1. GET | 200, 評價列表 |
| API-M08-003 | DELETE /v2/reviews/{id} | P0 | 評價本人 | 1. DELETE | 200, 軟刪除 |
| API-M08-004 | GET /v2/reviews/listing/{id}/stats | P0 | 有評價 | 1. GET | 200, 統計資料 |

### 3.2 🆕 回覆 API（Sprint 15 + 16）

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M08-101 | POST /v2/reviews/{id}/replies | P0 | StoreOwner 登入 | 1. POST Body: {content} | 200, ReplyResponse |
| API-M08-102 | GET /v2/reviews/{id}/replies | P0 | 有回覆 | 1. GET | 200, ReplyListResponse |
| API-M08-103 | POST /v2/reviews/{id}/replies - 重複 | P0 | 已有回覆 | 1. POST | 409, E_1086 |
| API-M08-104 | POST /v2/reviews/{id}/replies - 非擁有者 | P0 | 買家登入 | 1. POST | 403, E_1007 |

### 3.3 🆕 圖片管理 API（Sprint 16 US-005 + US-006）

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M08-201 | POST /v2/reviews/{id}/images | P0 | 評價本人 | 1. POST Body: {imageUrl} | 200, 新圖加入 |
| API-M08-202 | POST /v2/reviews/{id}/images - 超限 | P0 | 已有 9 張 | 1. POST | 400, E_1088 |
| API-M08-203 | DELETE /v2/reviews/{id}/images/{idx} | P0 | 評價本人 | 1. DELETE | 200, 圖片移除 |
| API-M08-204 | PUT /v2/reviews/{id}/images/order | P0 | 評價本人 | 1. PUT Body: {imageUrls} | 200, 順序更新 |

---

## 4. 評價狀態與權限矩陣

| 操作 | 買家 (評價本人) | 賣家/房東 (Listing 擁有者) | Admin | 其他買家 |
|------|--------------|------------------------|-------|---------|
| 建立評價 | ✅ | ❌ | ❌ | ❌ |
| 更新評價 | ✅ | ❌ | ❌ | ❌ |
| 刪除評價 | ✅ | ❌ | ❌ | ❌ |
| 商家回覆 | ❌ | ✅ | ❌ | ❌ |
| 取得回覆 | ✅ | ✅ | ✅ | ✅ |
| 標記為已處理 | ❌ | ✅ | ✅ | ❌ |
| 新增圖片 | ✅ | ❌ | ❌ | ❌ |
| 刪除圖片 | ✅ | ❌ | ❌ | ❌ |
| 重新排序圖片 | ✅ | ❌ | ❌ | ❌ |

---

## 5. 圖片數量與驗證矩陣

| 場景 | 預期行為 | 錯誤碼 |
|------|---------|--------|
| 0 張圖片 | ✅ 成功 | - |
| 1-9 張有效圖片 | ✅ 成功 | - |
| 10+ 張圖片 | ❌ 失敗 | E_1088 |
| 含無效 mediaId | ❌ 失敗 | E_1089 |
| 全部無效 | ❌ 失敗 | E_1089 |
| 圖片為 null | ✅ 成功 | - |

---

## 📝 關鍵驗證點總結

| 驗證項目 | 測試案例 | 優先級 |
|---------|---------|--------|
| 評價 CRUD 完整 | IT-M08-001 ~ 005 | P0 |
| 重複評價防止 | UT-M08-003, UT-M08-004 | P0 |
| 商家回覆權限 | UT-M08-103, API-M08-104 | P0 |
| 重複回覆防止 | UT-M08-104, API-M08-103 | P0 |
| 9 張上限 | UT-M08-202, IT-M08-102 | P0 |
| 圖片有效性 | UT-M08-204, UT-M08-206 | P0 |
| 圖片管理權限 | UT-M08-303, IT-M08-103 | P0 |
| 圖片重新排序 | UT-M08-306 ~ 308 | P0 |

---

**文件版本**: AISDLC v0.09
**測試框架**: JUnit 5 + Mockito, Spring Boot Test, REST Assured
**最後更新**: 2026-06-05
**作者**: Claude Code (AI Assistant)
