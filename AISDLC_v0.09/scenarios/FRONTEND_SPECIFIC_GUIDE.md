# 前端開發特化指引 / Frontend Development Specific Guide

**版本 / Version**: v0.09
**建立日期 / Created**: 2025-11-10
**文檔目的 / Purpose**: 提供 AISDLC 框架在前端開發專案中的特化考量和最佳實踐

---

## 📋 文檔概述 / Document Overview

### 目的 / Purpose

本指南為前端開發專案提供:
1. **前端特有的需求分析重點**
2. **前端架構設計考量**
3. **前端效能優化策略**
4. **前端測試策略**
5. **前端部署與 CI/CD**
6. **UI/UX 文檔要求**

### 適用情境 / Applicable Scenarios

本指南適用於以下 AISDLC 情境的前端開發面向:
- ✅ **Greenfield** - 前端新專案開發
- ✅ **Brownfield** - 前端既有專案維護
- ✅ **Refactoring** - 前端代碼重構
- ✅ **Integration** - 前端與後端 API 整合
- ✅ **Performance** - 前端效能優化
- ✅ **Testing** - 前端測試策略

---

## 🎨 1. Greenfield - 前端新專案開發特化

### 1.1 前端需求分析重點

#### 使用者體驗需求 (UX Requirements)
在 **PRD/FRD** 階段額外關注:

**📱 跨平台與響應式需求**:
- [ ] 支援裝置類型 (Desktop/Tablet/Mobile)
- [ ] 瀏覽器支援範圍 (Chrome/Firefox/Safari/Edge)
- [ ] 瀏覽器版本要求 (例如: Chrome 90+)
- [ ] 響應式斷點設計 (Breakpoints)
  - Mobile: < 768px
  - Tablet: 768px - 1024px
  - Desktop: > 1024px

**🎨 UI/UX 設計需求**:
- [ ] 設計系統/UI Kit (Material UI/Ant Design/自訂)
- [ ] 設計稿格式 (Figma/Sketch/Adobe XD)
- [ ] 品牌識別規範 (Logo/色彩/字型)
- [ ] 動畫與互動效果需求
- [ ] 無障礙設計需求 (WCAG 2.1 Level AA)

**⚡ 效能需求**:
- [ ] 首次內容繪製 (FCP) 目標: < 1.8s
- [ ] 最大內容繪製 (LCP) 目標: < 2.5s
- [ ] 首次輸入延遲 (FID) 目標: < 100ms
- [ ] 累積版面配置位移 (CLS) 目標: < 0.1
- [ ] Time to Interactive (TTI) 目標: < 3.8s

**📊 分析與追蹤需求**:
- [ ] Google Analytics / Adobe Analytics
- [ ] 使用者行為追蹤 (Hotjar/Mixpanel)
- [ ] 錯誤追蹤 (Sentry/Rollbar)
- [ ] 效能監控 (Lighthouse CI/WebPageTest)

---

### 1.2 前端技術選型考量

在 **階段 3: 技術選型** 時,額外評估:

#### 前端框架選擇
| 框架 | 適用場景 | 學習曲線 | 生態系統 | 效能 |
|------|---------|---------|---------|------|
| **React** | 通用型、大型專案 | 中 | 最成熟 | 優 |
| **Vue 3** | 快速開發、中小型專案 | 低 | 成熟 | 優 |
| **Angular** | 企業級、大型專案 | 高 | 完整 | 良 |
| **Svelte** | 高效能、小型專案 | 低-中 | 成長中 | 極優 |
| **Next.js** | React + SSR/SSG | 中 | 成熟 | 優 |
| **Nuxt.js** | Vue + SSR/SSG | 低-中 | 成熟 | 優 |

#### 狀態管理選擇
| 方案 | 適用框架 | 複雜度 | 適用場景 |
|------|---------|--------|---------|
| **Redux** | React | 中-高 | 大型複雜狀態 |
| **Zustand** | React | 低 | 中小型專案 |
| **Pinia** | Vue 3 | 低-中 | Vue 3 官方推薦 |
| **MobX** | React/Vue | 中 | 響應式狀態管理 |
| **Jotai** | React | 低 | 原子化狀態管理 |

#### UI 組件庫選擇
| 組件庫 | 適用框架 | 風格 | 自訂性 |
|--------|---------|------|--------|
| **Material UI** | React | Material Design | 高 |
| **Ant Design** | React/Vue | 企業級 | 中-高 |
| **Chakra UI** | React | 現代簡約 | 極高 |
| **Element Plus** | Vue 3 | 企業級 | 中 |
| **Vuetify** | Vue 3 | Material Design | 中 |
| **Headless UI** | React/Vue | 無樣式 | 極高 |

#### CSS 方案選擇
| 方案 | 特點 | 學習曲線 | 適用場景 |
|------|------|---------|---------|
| **Tailwind CSS** | Utility-first | 低 | 快速開發 |
| **CSS Modules** | 模組化 CSS | 低 | React 專案 |
| **Styled Components** | CSS-in-JS | 中 | React 專案 |
| **SCSS/SASS** | CSS 預處理器 | 低-中 | 傳統專案 |
| **Emotion** | CSS-in-JS | 中 | 高效能 CSS-in-JS |

---

### 1.3 前端架構設計 (SRD 階段)

#### 推薦架構模式

**1️⃣ Feature-based 架構 (推薦):**
```
src/
├── features/                 # 功能模組
│   ├── auth/                 # 認證功能
│   │   ├── components/       # 功能專屬元件
│   │   ├── hooks/            # 功能專屬 Hooks
│   │   ├── services/         # API 服務
│   │   ├── store/            # 狀態管理
│   │   └── types/            # 型別定義
│   ├── dashboard/
│   └── profile/
├── shared/                   # 共用資源
│   ├── components/           # 共用元件
│   ├── hooks/                # 共用 Hooks
│   ├── utils/                # 工具函式
│   └── constants/            # 常數
├── layouts/                  # 版面配置
├── routes/                   # 路由設定
└── styles/                   # 全域樣式
```

**優點**: 高內聚、易維護、易擴展
**適用**: 中大型專案

---

**2️⃣ Atomic Design 架構:**
```
src/
├── components/
│   ├── atoms/                # 原子層 (Button, Input)
│   ├── molecules/            # 分子層 (SearchBar, Card)
│   ├── organisms/            # 組織層 (Header, Sidebar)
│   ├── templates/            # 模板層 (PageTemplate)
│   └── pages/                # 頁面層 (HomePage)
├── hooks/
├── services/
└── store/
```

**優點**: 元件重用性高、設計系統友善
**適用**: 設計系統主導的專案

---

#### 前端資料流設計

**單向資料流 (Unidirectional Data Flow):**
```
User Action → Event Handler → State Update → UI Re-render
```

**狀態管理層次**:
1. **Local State** (元件內部狀態): `useState`, `useReducer`
2. **Shared State** (跨元件狀態): Context API, Zustand
3. **Global State** (全域狀態): Redux, Pinia
4. **Server State** (伺服器狀態): React Query, SWR

---

### 1.4 前端 API 整合設計

#### API 客戶端架構

**推薦方案 1: Axios + React Query / SWR**
```typescript
// services/api.ts
import axios from 'axios';

const apiClient = axios.create({
  baseURL: process.env.REACT_APP_API_URL,
  timeout: 10000,
});

// 請求攔截器
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 回應攔截器
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // 處理未授權
    }
    return Promise.reject(error);
  }
);

export default apiClient;
```

**React Query 使用範例**:
```typescript
import { useQuery, useMutation } from '@tanstack/react-query';
import apiClient from '@/services/api';

// GET 請求
export const useUsers = () => {
  return useQuery({
    queryKey: ['users'],
    queryFn: () => apiClient.get('/users').then(res => res.data),
  });
};

// POST 請求
export const useCreateUser = () => {
  return useMutation({
    mutationFn: (userData) => apiClient.post('/users', userData),
    onSuccess: () => {
      // 刷新列表
      queryClient.invalidateQueries(['users']);
    },
  });
};
```

---

#### API 錯誤處理策略

**分類錯誤處理**:
```typescript
const handleApiError = (error: AxiosError) => {
  if (error.response) {
    // 伺服器回應錯誤
    switch (error.response.status) {
      case 400:
        toast.error('請求參數錯誤');
        break;
      case 401:
        toast.error('請先登入');
        router.push('/login');
        break;
      case 403:
        toast.error('沒有權限');
        break;
      case 404:
        toast.error('資源不存在');
        break;
      case 500:
        toast.error('伺服器錯誤');
        break;
      default:
        toast.error('未知錯誤');
    }
  } else if (error.request) {
    // 請求已發送但沒有回應
    toast.error('網路連線失敗');
  } else {
    // 請求設定錯誤
    toast.error('請求設定錯誤');
  }
};
```

---

## ⚡ 2. Performance - 前端效能優化特化

### 2.1 前端效能分析工具

**必備工具**:
- ✅ **Lighthouse** (整體效能評分)
- ✅ **Chrome DevTools Performance** (詳細效能剖析)
- ✅ **React DevTools Profiler** (React 元件效能)
- ✅ **Bundle Analyzer** (Bundle 大小分析)
- ✅ **WebPageTest** (真實環境測試)

**效能指標 (Web Vitals)**:
- ✅ **LCP** (Largest Contentful Paint): < 2.5s
- ✅ **FID** (First Input Delay): < 100ms
- ✅ **CLS** (Cumulative Layout Shift): < 0.1
- ✅ **FCP** (First Contentful Paint): < 1.8s
- ✅ **TTI** (Time to Interactive): < 3.8s

---

### 2.2 前端效能優化策略

#### 策略 1: 程式碼分割 (Code Splitting)

**React Lazy Loading**:
```typescript
import { lazy, Suspense } from 'react';

// 路由層級的程式碼分割
const Dashboard = lazy(() => import('@/pages/Dashboard'));
const Profile = lazy(() => import('@/pages/Profile'));

function App() {
  return (
    <Suspense fallback={<Loading />}>
      <Routes>
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/profile" element={<Profile />} />
      </Routes>
    </Suspense>
  );
}
```

**預期效果**: 首次載入 Bundle 減少 40-60%

---

#### 策略 2: 圖片優化

**圖片格式選擇**:
- **WebP**: 優先使用,體積比 JPEG/PNG 小 30%
- **AVIF**: 最佳壓縮率,體積比 WebP 小 20%
- **JPEG**: 相片類圖片備選
- **SVG**: 圖示、Logo

**響應式圖片**:
```html
<picture>
  <source srcset="image.avif" type="image/avif">
  <source srcset="image.webp" type="image/webp">
  <img src="image.jpg" alt="Description" loading="lazy">
</picture>
```

**圖片 CDN**:
- ✅ Cloudinary
- ✅ Imgix
- ✅ Cloudflare Images

**預期效果**: 圖片載入速度提升 50-70%

---

#### 策略 3: 虛擬滾動 (Virtual Scrolling)

**適用場景**: 長列表、表格

**React 推薦套件**:
- **react-window** (輕量級)
- **react-virtualized** (功能豐富)

**範例**:
```typescript
import { FixedSizeList } from 'react-window';

const Row = ({ index, style }) => (
  <div style={style}>Row {index}</div>
);

function LongList({ items }) {
  return (
    <FixedSizeList
      height={600}
      itemCount={items.length}
      itemSize={50}
      width="100%"
    >
      {Row}
    </FixedSizeList>
  );
}
```

**預期效果**: 列表渲染時間從 2000ms → 50ms

---

#### 策略 4: Memoization (記憶化)

**React.memo**:
```typescript
const ExpensiveComponent = React.memo(({ data }) => {
  // 只在 data 改變時重新渲染
  return <div>{/* 複雜的渲染邏輯 */}</div>;
});
```

**useMemo**:
```typescript
const expensiveValue = useMemo(() => {
  return computeExpensiveValue(a, b);
}, [a, b]);
```

**useCallback**:
```typescript
const memoizedCallback = useCallback(() => {
  doSomething(a, b);
}, [a, b]);
```

**預期效果**: 減少不必要的重新渲染 60-80%

---

#### 策略 5: 資料快取策略

**React Query 快取設定**:
```typescript
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000,      // 5 分鐘內視為新鮮
      cacheTime: 10 * 60 * 1000,     // 快取保留 10 分鐘
      refetchOnWindowFocus: false,    // 視窗聚焦不重新取得
      retry: 1,                       // 失敗重試 1 次
    },
  },
});
```

**LocalStorage/SessionStorage 快取**:
```typescript
const getCachedData = (key: string, ttl: number) => {
  const cached = localStorage.getItem(key);
  if (!cached) return null;

  const { data, timestamp } = JSON.parse(cached);
  if (Date.now() - timestamp > ttl) {
    localStorage.removeItem(key);
    return null;
  }

  return data;
};
```

**預期效果**: 重複請求減少 70-90%

---

## 🧪 3. Testing - 前端測試特化

### 3.1 前端測試金字塔

```
         /\
        /  \  E2E Tests (10%)
       /----\
      /      \  Integration Tests (30%)
     /--------\
    /          \  Unit Tests (60%)
   /____________\
```

### 3.2 前端測試策略

#### 層級 1: 單元測試 (60%)

**測試範圍**:
- ✅ 工具函式 (Utils)
- ✅ Custom Hooks
- ✅ 純元件 (Presentational Components)
- ✅ 狀態管理 (Store/Reducer)

**推薦工具**:
- **Jest** (測試框架)
- **React Testing Library** (React 元件測試)
- **Vue Test Utils** (Vue 元件測試)
- **Vitest** (快速測試框架)

**範例 - 測試 Custom Hook**:
```typescript
import { renderHook, act } from '@testing-library/react';
import useCounter from '@/hooks/useCounter';

describe('useCounter', () => {
  it('應該初始化為 0', () => {
    const { result } = renderHook(() => useCounter());
    expect(result.current.count).toBe(0);
  });

  it('應該能夠增加計數', () => {
    const { result } = renderHook(() => useCounter());
    act(() => {
      result.current.increment();
    });
    expect(result.current.count).toBe(1);
  });
});
```

---

#### 層級 2: 整合測試 (30%)

**測試範圍**:
- ✅ 多個元件的互動
- ✅ API 整合 (使用 Mock)
- ✅ 路由導航
- ✅ 表單提交流程

**推薦工具**:
- **React Testing Library** (使用者互動測試)
- **MSW** (Mock Service Worker - API Mock)

**範例 - 測試表單提交**:
```typescript
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '@/mocks/server';
import LoginForm from '@/components/LoginForm';

describe('LoginForm', () => {
  it('應該成功登入', async () => {
    const user = userEvent.setup();
    render(<LoginForm />);

    await user.type(screen.getByLabelText('Email'), 'test@example.com');
    await user.type(screen.getByLabelText('Password'), 'password123');
    await user.click(screen.getByRole('button', { name: 'Login' }));

    await waitFor(() => {
      expect(screen.getByText('登入成功')).toBeInTheDocument();
    });
  });
});
```

---

#### 層級 3: E2E 測試 (10%)

**測試範圍**:
- ✅ 關鍵業務流程 (購物車結帳、註冊登入)
- ✅ 跨頁面互動
- ✅ 真實 API 整合

**推薦工具**:
- **Playwright** (推薦,多瀏覽器支援)
- **Cypress** (開發體驗佳)

**範例 - Playwright E2E 測試**:
```typescript
import { test, expect } from '@playwright/test';

test('完整購物流程', async ({ page }) => {
  // 訪問首頁
  await page.goto('http://localhost:3000');

  // 搜尋商品
  await page.fill('input[name="search"]', 'iPhone');
  await page.click('button[type="submit"]');

  // 加入購物車
  await page.click('text=加入購物車');
  await expect(page.locator('.cart-count')).toHaveText('1');

  // 前往結帳
  await page.click('text=前往結帳');
  await page.fill('input[name="address"]', '台北市信義區');
  await page.click('button:has-text("確認訂單")');

  // 驗證訂單成功
  await expect(page.locator('h1')).toHaveText('訂單完成');
});
```

---

### 3.3 前端測試覆蓋率目標

| 專案規模 | 單元測試 | 整合測試 | E2E 測試 | 總覆蓋率 |
|---------|---------|---------|---------|---------|
| **小型** | ≥ 60% | ≥ 30% | 關鍵流程 | ≥ 60% |
| **中型** | ≥ 80% | ≥ 50% | 關鍵流程 | ≥ 80% |
| **大型** | ≥ 90% | ≥ 70% | 所有流程 | ≥ 90% |

---

## 🚀 4. DevOps - 前端部署與 CI/CD

### 4.1 前端建置優化

**建置時間優化**:
```javascript
// vite.config.ts
export default defineConfig({
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom'],
          'ui-vendor': ['@mui/material'],
          'utils-vendor': ['lodash', 'date-fns'],
        },
      },
    },
  },
});
```

**預期效果**: 建置時間從 3min → 1min

---

### 4.2 前端 CI/CD Pipeline

**推薦 Pipeline 結構**:
```yaml
name: Frontend CI/CD

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: 18
      - run: npm ci
      - run: npm run lint
      - run: npm run test:unit
      - run: npm run test:e2e

  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - run: npm ci
      - run: npm run build
      - uses: actions/upload-artifact@v3
        with:
          name: dist
          path: dist/

  deploy:
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/download-artifact@v3
      - run: npm run deploy
```

---

### 4.3 前端部署方案選擇

| 方案 | 適用場景 | 成本 | 優點 | 缺點 |
|------|---------|------|------|------|
| **Vercel** | Next.js/React 專案 | 免費起 | 極快部署、自動 CI/CD | 中國訪問慢 |
| **Netlify** | 靜態網站、JAMstack | 免費起 | 簡單易用、CDN 快 | 功能限制 |
| **AWS S3 + CloudFront** | 任何框架 | 低 | 彈性高、可擴展 | 設定複雜 |
| **Firebase Hosting** | Google 生態系 | 免費起 | 快速部署、CDN | 功能限制 |
| **Cloudflare Pages** | 任何框架 | 免費起 | 極快 CDN、低成本 | 功能較新 |

---

## 📚 5. Documentation - 前端文檔特化

### 5.1 前端必要文檔清單

**技術文檔**:
- ✅ **元件庫文檔** (Storybook)
- ✅ **API 整合文檔**
- ✅ **狀態管理架構文檔**
- ✅ **路由結構文檔**
- ✅ **建置與部署文檔**

**設計文檔**:
- ✅ **設計系統文檔**
- ✅ **UI Kit 使用指南**
- ✅ **響應式設計規範**
- ✅ **無障礙設計指南 (A11y)**

---

### 5.2 元件文檔 - Storybook

**安裝 Storybook**:
```bash
npx storybook@latest init
```

**元件 Story 範例**:
```typescript
// Button.stories.tsx
import type { Meta, StoryObj } from '@storybook/react';
import { Button } from './Button';

const meta: Meta<typeof Button> = {
  title: 'Components/Button',
  component: Button,
  tags: ['autodocs'],
};

export default meta;
type Story = StoryObj<typeof Button>;

export const Primary: Story = {
  args: {
    label: 'Button',
    variant: 'primary',
  },
};

export const Secondary: Story = {
  args: {
    label: 'Button',
    variant: 'secondary',
  },
};
```

---

## 📋 6. 前端檢查清單 / Frontend Checklist

### 開發前檢查清單

- [ ] 設計稿已完成且經過 Review
- [ ] API 規格已定義
- [ ] UI Kit / 元件庫已選定
- [ ] 狀態管理方案已選定
- [ ] 瀏覽器支援範圍已確認
- [ ] 無障礙需求已確認

### 開發中檢查清單

- [ ] 元件命名遵循規範
- [ ] 使用 TypeScript 型別定義
- [ ] API 錯誤處理完整
- [ ] 載入狀態處理完整
- [ ] 響應式設計已實作
- [ ] 單元測試覆蓋率 ≥ 目標

### 上線前檢查清單

- [ ] Lighthouse 效能評分 ≥ 90
- [ ] 所有測試通過 (Unit/Integration/E2E)
- [ ] 跨瀏覽器測試完成
- [ ] 跨裝置測試完成
- [ ] SEO 優化完成 (如需要)
- [ ] 錯誤追蹤工具已設定
- [ ] 分析工具已設定
- [ ] CDN 已設定
- [ ] 環境變數已配置

---

## 📚 相關文檔 / Related Documents

- [SCALING_GUIDE.md](SCALING_GUIDE.md) - 規模化調整指引
- [ERROR_RECOVERY_GUIDE.md](ERROR_RECOVERY_GUIDE.md) - 錯誤恢復指南
- [各情境 SOP](.) - 九大情境的詳細流程

---

## 🔄 文檔維護記錄 / Document History

| 版本 | 日期 | 變更內容 | 作者 |
|-----|------|---------|------|
| 1.0 | 2025-11-10 | 初版建立,定義前端特化內容 | Claude Code |

---

**維護者 / Maintainer**: AISDLC Framework Team
**最後更新 / Last Updated**: 2025-11-10
**文檔狀態 / Status**: ✅ Active
