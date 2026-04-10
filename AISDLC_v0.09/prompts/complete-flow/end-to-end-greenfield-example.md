# Greenfield 端到端完整範例
# End-to-End Greenfield Example

**版本**: v0.02
**範例專案**: 任務管理 Web App (TaskMaster)
**預計時間**: 3-5 天（AI 輔助可壓縮至數小時）
**最後更新**: 2025-10-22

---

## 📋 專案背景

### 業務需求
一家新創公司想開發一個簡單的任務管理 Web App，幫助團隊協作。

### 核心功能需求
- 用戶可以註冊/登入
- 用戶可以創建/編輯/刪除任務
- 任務可以設定優先級（高/中/低）
- 任務可以設定截止日期
- 用戶可以將任務標記為完成
- 用戶可以按狀態/優先級篩選任務

### 非功能需求
- 回應時間 < 1 秒
- 支援 100 並發用戶
- 資料持久化
- 安全性（認證/授權）

---

## 🚀 完整執行流程

### 階段 1：專案啟動（10 分鐘）

#### 用戶指令
```
我想開發一個全新的任務管理 Web App。

專案名稱：TaskMaster
業務目標：幫助團隊更好地管理任務和協作
目標用戶：小型團隊（5-20 人）

核心功能：
- 用戶註冊/登入
- 任務 CRUD
- 任務狀態管理
- 任務篩選

請使用 Greenfield 情境協助我。
載入 AISDLC_INIT.md，執行 greenfield-complete-flow。
```

#### AISDLC 回應
```
✅ 已載入 AISDLC v0.02
✅ 已啟動 Greenfield 情境
✅ 已調用 Agents：
   - PM/PO agent (Victoria)
   - SA agent (Amanda)
   - SD architect (Marcus)

📋 開始執行 Greenfield SOP 階段 1：專案啟動與需求收集

【Victoria (PM/PO)】您好！我會協助您梳理業務目標。

讓我確認幾個關鍵問題：

1. 商業目標優先級（請選擇編號）：
   1. 快速上線驗證市場（3 個月 MVP）
   2. 穩健開發確保品質（6 個月完整版）
   3. 其他（請說明）

2. 主要競爭優勢：
   1. 簡單易用（簡化功能，專注核心）
   2. 功能豐富（與 Trello/Asana 競爭）
   3. 其他（請說明）

3. 營收模式：
   1. 免費版 + 付費進階功能
   2. 完全免費
   3. 訂閱制
   4. 其他

🔴 請選擇或補充說明。
```

#### 用戶回應
```
1. 1（快速上線驗證市場）
2. 1（簡單易用）
3. 1（免費版 + 付費進階功能）

補充：免費版支援 5 個用戶，付費版無限用戶。
```

#### AISDLC 回應
```
【Victoria】太好了！我已記錄：
✅ MVP 策略：3 個月上線
✅ 定位：簡單易用的任務管理工具
✅ 營收：Freemium 模式（5 用戶免費 → 無限用戶付費）

【Amanda (SA)】現在由我接手需求深度提取。

我已初步識別以下需求：

核心功能需求：
- US-001：用戶註冊與登入
- US-002：創建任務
- US-003：編輯任務
- US-004：刪除任務
- US-005：任務狀態管理（進行中/已完成）
- US-006：任務篩選與排序

非功能需求：
- NF-001：回應時間 < 1 秒
- NF-002：支援 100 並發用戶
- NF-003：資料持久化（資料不丟失）
- NF-004：安全性（認證/授權）

🔴 這些需求是否涵蓋您的預期？有需要補充的嗎？
```

#### 用戶回應
```
是的，這些需求都正確。

補充一個需求：
- 任務可以設定優先級（高/中/低）

其他都很好。
```

---

### 階段 2：需求深度提取（30 分鐘）

#### AISDLC 執行
```
【Amanda】已新增需求：
✅ US-007：任務優先級設定

現在執行 requirements-extraction workflow...

【執行中】
✓ 分析功能需求
✓ 識別隱含需求
✓ 提取非功能需求
✓ 識別風險與假設

【Amanda】需求提取完成！我識別了一些隱含需求：

隱含需求：
1. 任務應該有唯一 ID
2. 任務應該記錄創建時間和最後修改時間
3. 用戶密碼應該加密存儲
4. 用戶應該能夠登出
5. 系統應該處理重複註冊（Email 已存在）

🔴 這些隱含需求是否合理？請確認。
```

#### 用戶回應
```
是的，這些都很合理。請繼續。
```

---

### 階段 3：技術棧選型（20 分鐘）

#### AISDLC 執行
```
【Marcus (SD Architect)】現在由我協助技術選型。

執行 tech-stack-selection-flow...

基於您的需求（Web App、快速上線、小型團隊），我評估了以下技術棧方案：

方案 1：現代 JavaScript 全棧
- 前端：React + TypeScript + Tailwind CSS
- 後端：Node.js + Express.js
- 資料庫：PostgreSQL
- 認證：JWT
- 部署：Vercel (前端) + Railway (後端)

✅ 優點：
  - 全 JavaScript，團隊學習曲線低
  - React 生態豐富
  - 快速開發（現成 UI 組件庫）
  - 部署簡單（一鍵部署）
❌ 缺點：
  - Node.js 效能略遜於編譯型語言
  - 類型安全依賴 TypeScript

方案 2：Next.js 全端框架
- 全端框架：Next.js 14 (App Router)
- 資料庫：PostgreSQL + Prisma ORM
- 認證：NextAuth.js
- 部署：Vercel

✅ 優點：
  - 前後端一體（減少複雜度）
  - SSR/SSG 提升 SEO 和效能
  - 部署極簡（一鍵部署）
  - API Routes 內建
❌ 缺點：
  - 框架綁定較深
  - 後端擴展性略受限

方案 3：Python 後端 + React 前端
- 前端：React + TypeScript
- 後端：Python + FastAPI
- 資料庫：PostgreSQL + SQLAlchemy
- 認證：JWT
- 部署：Netlify (前端) + Heroku/Railway (後端)

✅ 優點：
  - FastAPI 效能優異
  - Python 生態豐富（未來擴展 AI 功能方便）
  - 類型安全（TypeScript + Pydantic）
❌ 缺點：
  - 需要維護兩套技術棧
  - 學習曲線略高

🔴 推薦：方案 2（Next.js）
理由：快速上線、部署簡單、適合 MVP，未來可升級。

您選擇哪個方案？（1/2/3 或其他建議）
```

#### 用戶回應
```
2

我認同 Next.js 的簡潔性，適合我們的 MVP 策略。
```

---

### 階段 4：需求驗證與文檔化（40 分鐘）

#### AISDLC 執行
```
【Amanda】太好了！現在執行 validation-documentation workflow。

【Beatrice (BA)】我會協助驗證需求。

🔄 執行多 Agent 協作驗證...

【驗證完成】
✅ 所有 User Stories 符合 INVEST 原則
✅ Acceptance Criteria 清晰可測試
✅ 與利害關係人（您）確認無誤

【Amanda】正在生成 FRD...

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📄 FRD_TaskMaster_v1.0.md 已生成
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 摘要
- User Stories: 8 個
- Acceptance Criteria: 24 個
- 功能模組: 2 個（用戶管理、任務管理）

## User Stories 列表

### US-001: 用戶註冊
**As a** 新用戶
**I want to** 註冊帳號
**So that** 我可以使用任務管理系統

**Acceptance Criteria:**
- AC-001-1: 用戶輸入 Email 和密碼後點擊註冊按鈕
- AC-001-2: 系統驗證 Email 格式（valid email）
- AC-001-3: 系統驗證密碼強度（至少 8 字元，包含大小寫和數字）
- AC-001-4: Email 已存在時顯示錯誤「Email 已被使用」
- AC-001-5: 註冊成功後自動登入並跳轉到任務頁面

**優先級**: Must-have
**預估工時**: 4 小時

### US-002: 用戶登入
[略]

### US-003: 創建任務
[略]

...（共 8 個 User Stories）

🔴 FRD 已產出，請審查。是否需要修改？
```

#### 用戶回應
```
看起來很完整！請繼續下一階段。
```

---

### 階段 5：系統架構設計（60 分鐘）

#### AISDLC 執行
```
【Marcus】現在執行 user-story-design workflow。

開始設計系統架構...

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📐 SRD_TaskMaster_v1.0.md 已生成
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

## 系統架構

### 整體架構（3-Layer Architecture）
```
┌─────────────────────────────────┐
│      Presentation Layer         │
│  (Next.js App Router + React)   │
├─────────────────────────────────┤
│      Business Logic Layer       │
│   (API Routes + Server Actions) │
├─────────────────────────────────┤
│       Data Access Layer         │
│    (Prisma ORM + PostgreSQL)    │
└─────────────────────────────────┘
```

### 資料模型（ERD）

```sql
-- User 表
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- Task 表
CREATE TABLE tasks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title VARCHAR(255) NOT NULL,
  description TEXT,
  status VARCHAR(20) DEFAULT 'todo',  -- todo, in_progress, done
  priority VARCHAR(10) DEFAULT 'medium',  -- low, medium, high
  due_date DATE,
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_tasks_user_id ON tasks(user_id);
CREATE INDEX idx_tasks_status ON tasks(status);
```

### 目錄結構
```
task-master/
├── app/
│   ├── (auth)/
│   │   ├── login/page.tsx
│   │   └── register/page.tsx
│   ├── (dashboard)/
│   │   ├── tasks/page.tsx
│   │   └── layout.tsx
│   ├── api/
│   │   ├── auth/
│   │   │   ├── login/route.ts
│   │   │   └── register/route.ts
│   │   └── tasks/
│   │       ├── route.ts          # GET /POST
│   │       └── [id]/route.ts     # PUT /DELETE
│   └── layout.tsx
├── components/
│   ├── TaskCard.tsx
│   ├── TaskForm.tsx
│   └── TaskFilter.tsx
├── lib/
│   ├── prisma.ts
│   ├── auth.ts
│   └── utils.ts
├── prisma/
│   └── schema.prisma
└── package.json
```

🔴 系統架構設計完成，請確認。
```

#### 用戶回應
```
架構設計很清晰！請繼續。
```

---

### 階段 6：API 規格制定（30 分鐘）

#### AISDLC 執行
```
【Marcus】執行 api-specification workflow...

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📡 API Specifications 已生成
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

### API 清單

#### 認證 APIs
- POST /api/auth/register - 用戶註冊
- POST /api/auth/login - 用戶登入
- POST /api/auth/logout - 用戶登出

#### 任務 APIs
- GET /api/tasks - 獲取任務列表（支援篩選）
- POST /api/tasks - 創建任務
- PUT /api/tasks/[id] - 更新任務
- DELETE /api/tasks/[id] - 刪除任務

### 範例：POST /api/auth/register

**Request:**
```json
{
  "email": "user@example.com",
  "password": "SecurePass123"
}
```

**Response (Success):**
```json
{
  "success": true,
  "data": {
    "user": {
      "id": "uuid-123",
      "email": "user@example.com"
    },
    "token": "jwt-token-here"
  }
}
```

**Response (Error - Email Exists):**
```json
{
  "success": false,
  "error": {
    "code": "EMAIL_EXISTS",
    "message": "Email 已被使用"
  }
}
```

...（所有 API 詳細規格已產出）

📄 完整 API 規格請參考：
- docs/api/API_Auth_Register.md
- docs/api/API_Auth_Login.md
- docs/api/API_Tasks_List.md
- docs/api/API_Tasks_Create.md
- docs/api/API_Tasks_Update.md
- docs/api/API_Tasks_Delete.md

🔴 API 規格已完成，請確認。
```

---

### 階段 7：開發準備與移交（20 分鐘）

#### AISDLC 執行
```
【Marcus】執行階段 8：開發準備與移交。

正在準備開發移交材料...

✅ 已創建 Git Repository 初始化腳本
✅ 已生成 package.json
✅ 已準備 Prisma Schema
✅ 已生成 環境變數模板 (.env.example)
✅ 已產出 Developer_Guideline.md

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📦 開發移交包（Development Handoff Package）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

### 產出文檔清單
1. ✅ PRD_TaskMaster_v1.0.md
2. ✅ FRD_TaskMaster_v1.0.md
3. ✅ SRD_TaskMaster_v1.0.md
4. ✅ API_Index.md + 6 個 API 規格文檔
5. ✅ Developer_Guideline.md
6. ✅ .env.example
7. ✅ prisma/schema.prisma
8. ✅ package.json

### 快速啟動指令
```bash
# 1. Clone repository
git clone <repo-url>
cd task-master

# 2. Install dependencies
npm install

# 3. Setup environment variables
cp .env.example .env
# Edit .env with your database URL

# 4. Setup database
npx prisma migrate dev --name init
npx prisma generate

# 5. Run development server
npm run dev
```

### Sprint 1 任務分配（建議 2 週）
- [ ] 任務 1.1: 環境初始化（0.5 天）
- [ ] 任務 1.2: 資料庫設定與 Prisma（0.5 天）
- [ ] 任務 1.3: 用戶註冊 API（1 天）
- [ ] 任務 1.4: 用戶登入 API + JWT（1 天）
- [ ] 任務 1.5: 註冊/登入 UI（1 天）
- [ ] 任務 1.6: 創建任務 API（0.5 天）
- [ ] 任務 1.7: 任務列表 API（0.5 天）
- [ ] 任務 1.8: 任務 CRUD UI（2 天）
- [ ] 任務 1.9: 測試與 Bug 修復（2 天）
- [ ] 任務 1.10: 部署到 Vercel（0.5 天）

**總計**: 10 天（2 週 Sprint）

🎉 恭喜！TaskMaster 專案的需求分析和技術設計階段已完成！

📂 所有文檔已保存至：
   /project/task-master/docs/

🚀 團隊現在可以開始 Sprint 1 開發！
```

---

## 📊 執行總結

### 完成時間
- **預計時間**：3-5 天（傳統方式）
- **實際時間**：約 3-4 小時（AI 輔助）
- **加速比**：約 15-20x

### 產出文檔
1. PRD（Product Requirements Document）- 5 頁
2. FRD（Functional Requirements Document）- 15 頁
3. SRD（System Requirements Document）- 20 頁
4. API Specifications - 6 個 API 詳細規格
5. Developer Guideline - 開發者指南
6. 初始化程式碼（Prisma Schema, package.json, .env.example）

### 品質保證
- ✅ 所有 User Stories 符合 INVEST 原則
- ✅ Acceptance Criteria 清晰可測試
- ✅ PRD → FRD → SRD → API 追蹤鏈完整
- ✅ 技術選型有明確理由
- ✅ 文檔術語一致

---

## 💡 關鍵經驗

### 成功因素
1. **明確初始需求**：雖然簡單，但已包含核心功能
2. **善用編號選項協議**：快速決策，不卡關
3. **信任 AI 建議**：在關鍵確認點回應即可
4. **完整追蹤鏈**：從需求到 API 規格環環相扣

### 人機協作亮點
- **AI 主導**：需求提取、架構設計、文檔生成
- **人類決策**：商業策略、技術棧選擇、優先級排序
- **確認點設計**：只在關鍵點 🔴 需要人類確認，其他自動推進

---

## 🎓 延伸學習

### 下一步
1. 執行 Testing SOP，建立測試策略
2. 執行 DevOps SOP，設定 CI/CD
3. 執行 Documentation SOP，補充用戶手冊

### 其他情境範例
- [Brownfield 範例](./brownfield-maintenance-example.md) - 既有系統修改
- [Performance 範例](./performance-optimization-example.md) - 效能優化
- [Integration 範例](./third-party-integration-example.md) - 第三方整合

---

**版本**: v0.02
**範例完成時間**: 3-4 小時
**維護者**: AISDLC Framework Team
**最後更新**: 2025-10-22
