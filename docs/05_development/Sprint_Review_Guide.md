# Sprint Review 展示指南 / Sprint Review Demo Guide

> **Sprint 編號**: Sprint 1
> **期間**: 2026-04-15 ~ 2026-04-28 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-04-19

---

## 目的

本文件提供 Sprint Review 會議的標準流程，確保團隊能夠有效展示 Sprint 1 成果給利害關係人確認。

---

## Sprint Review 會議議程

| 時間 | 項目 | 負責人 | 說明 |
|------|------|--------|------|
| 5 min | 開場簡報 | PM/PO | 說明 Sprint 1 目標與範圍 |
| 20-30 min | Demo 演示 | Dev | 展示已完成的功能（實際操作） |
| 15 min | 回顧與討論 | Dev/QA | 燃盡圖、velocity、阻礙與學到的教訓 |
| 10 min | 收集回饋 | PM/PO | 利害關係人意見與需求調整 |

---

## 第一步：啟動 Docker 服務

在專案根目錄執行：

```bash
cd /Users/wuweihong/Cursor_Project/E-Commerce_Minimax
docker compose up -d
```

等待所有服務啟動（約 30-60 秒）：

```bash
# 查看服務狀態
docker compose ps

# 查看服務健康狀態
docker compose logs --tail=20
```

---

## 第二步：確認服務健康

```bash
# Backend 健康檢查（注意：路徑包含 /api 前綴）
curl http://localhost:8080/api/actuator/health

# Frontend 健康檢查
curl http://localhost:3000/health
```

**預期輸出**：`{"status":"UP"}`

如果服務未正常啟動：

```bash
# 查看特定服務 logs
docker compose logs backend
docker compose logs frontend

# 重新啟動特定服務
docker compose restart backend
docker compose restart frontend

# 停止所有服務
docker compose down
```

---

## 第三步：展示會員註冊（API-M03-001）

**功能**：新用戶註冊成為會員

```bash
curl -X POST http://localhost:8080/api/v2/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "demo@example.com",
    "password": "Demo1234",
    "userType": "BUYER"
  }'
```

**預期輸出**：
```json
{
  "code": 201,
  "message": "Registration successful",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "demo@example.com",
    "userType": "BUYER",
    "createdAt": "2026-04-19T10:30:00.000Z"
  }
}
```

**驗證點**：
- [ ] AC-001: 成功註冊返回 201 和 userId
- [ ] AC-004: 預設角色為 BUYER

**錯誤場景展示**：
```bash
# Email 已被註冊 (409)
curl -X POST http://localhost:8080/api/v2/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "demo@example.com",
    "password": "Demo1234",
    "userType": "BUYER"
  }'
```

---

## 第四步：展示會員登入（API-M03-002）

**功能**：會員登入，系統回傳 Access Token 和 Refresh Token

```bash
curl -X POST http://localhost:8080/api/v2/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "demo@example.com",
    "password": "Demo1234"
  }'
```

**預期輸出**：
```json
{
  "code": 200,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 1800,
    "tokenType": "Bearer"
  }
}
```

**驗證點**：
- [ ] AC-001: 成功登入返回 200 和 accessToken + refreshToken

**錯誤場景展示**：
```bash
# 錯誤密碼 (401)
curl -X POST http://localhost:8080/api/v2/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "demo@example.com",
    "password": "WrongPassword"
  }'
```

---

## 第五步：展示取得用戶資訊（API-M03-005）

**功能**：取得已登入會員的個人資訊

```bash
# 將 <ACCESS_TOKEN> 替換為登入時取得的 accessToken
curl -X GET http://localhost:8080/api/v2/auth/me \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

**預期輸出**：
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "email": "demo@example.com",
    "userType": "BUYER",
    "status": "ACTIVE",
    "emailVerified": false,
    "profile": {
      "displayName": "User",
      "phone": null,
      "avatarUrl": null
    },
    "tenants": [],
    "createdAt": "2026-04-19T10:30:00.000Z"
  }
}
```

**驗證點**：
- [ ] AC-001: 有效 Token 返回 200 和用戶資訊
- [ ] AC-003: 回傳包含 userId, email, profile, tenants

---

## 第六步：展示 Token 刷新（API-M03-003）

**功能**：使用 Refresh Token 取得新的 Access Token

```bash
# 將 <REFRESH_TOKEN> 替換為登入時取得的 refreshToken
curl -X POST http://localhost:8080/api/v2/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<REFRESH_TOKEN>"
  }'
```

**預期輸出**：
```json
{
  "code": 200,
  "message": "Token refreshed successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 1800,
    "tokenType": "Bearer"
  }
}
```

**驗證點**：
- [ ] AC-001: 有效 Refresh Token 返回新 Access Token

---

## 第七步：展示會員登出（API-M03-004）

**功能**：登出會員，失效 Refresh Token

```bash
curl -X POST http://localhost:8080/api/v2/auth/logout \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "<REFRESH_TOKEN>"
  }'
```

**預期輸出**：
```json
{
  "code": 200,
  "message": "Logout successful",
  "data": null
}
```

**驗證點**：
- [ ] AC-001: 成功登出返回 200
- [ ] AC-002: Refresh Token 在 Redis blacklist 中

---

## 第八步：開啟前端 UI 展示

```bash
# 在瀏覽器開啟前端
open http://localhost:3000
```

**建議展示流程**：

1. **註冊頁面**
   - 開啟瀏覽器訪問 http://localhost:3000/register
   - 填入 email、密碼進行註冊
   - 展示前端驗證（密碼格式要求）

2. **登入頁面**
   - 訪問 http://localhost:3000/login
   - 使用剛註冊的帳號登入
   - 展示 Token 儲存（在瀏覽器 DevTools > Application > Cookies）

3. **用戶資料頁面**
   - 登入後訪問個人頁面
   - 展示取得的用戶資訊

4. **API 文件**
   - 開啟瀏覽器訪問 http://localhost:8080/swagger-ui.html
   - 展示完整的 API 規格文件

---

## 展示檢查清單

| 功能 | API | 驗證方式 | 狀態 |
|------|-----|---------|------|
| 會員註冊 | POST `/api/v2/auth/register` | 成功回傳 userId | [x] ✅ |
| 會員登入 | POST `/api/v2/auth/login` | 取得 accessToken + refreshToken | [x] ✅ |
| 取得用戶資訊 | GET `/api/v2/auth/me` | 顯示個人資料與 tenants | [x] ✅ |
| Token 刷新 | POST `/api/v2/auth/refresh` | 取得新 accessToken | [x] ✅ |
| 會員登出 | POST `/api/v2/auth/logout` | Token 失效 | [x] ✅ |

---

## Sprint 1 完成狀態

| US ID | 標題 | SP | 狀態 |
|-------|------|-----|------|
| US-M03-001 | 會員註冊 | 3 | [x] ✅ 已完成 |
| US-M03-002 | 會員登入 | 3 | [x] ✅ 已完成 |
| US-M03-003 | JWT 刷新 | 3 | [x] ✅ 已完成 |
| US-M03-004 | 會員登出 | 2 | [x] ✅ 已完成 |
| US-M03-005 | 取得當前用戶資訊 | 2 | [x] ✅ 已完成 |
| **Total** | | **13** | **13 SP 完成** |

---

## 利害關係人回饋收集

在 Sprint Review 結束前，請收集以下回饋：

| 回饋類型 | 內容 | 備註 |
|---------|------|------|
| 功能滿意度 | 已展示功能是否滿足需求？ | |
| UI/UX 建議 | 前端介面是否有需要改進之處？ | |
| API 設計 | API 規格是否清晰、易用？ | |
| 文件完整性 | API 文件是否足夠詳細？ | |
| 下一 Sprint 優先級 | 是否有新需求或優先級調整？ | |

---

## 附錄：常用指令速查

```bash
# 啟動服務
docker compose up -d

# 停止服務
docker compose down

# 查看服務狀態
docker compose ps

# 查看 logs
docker compose logs -f backend
docker compose logs -f frontend

# 重新啟動服務
docker compose restart backend
docker compose restart frontend

# 執行測試
cd backend && mvn test

# 查看 API 文件
open http://localhost:8080/swagger-ui.html
```

---

**文件版本**: v1.0
**最後更新**: 2026-04-19
