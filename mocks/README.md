# Mocks 目錄說明 / Mocks Directory Guide

> **用途**：存放本機 Mock 服務的設定檔，供 `docker-compose.mock.yml` 使用

---

## 📁 目錄結構

```
mocks/
├── README.md              # 本文件
└── mockoon-data.json      # Mockoon API Mock 設定
```

---

## 🚀 快速啟動

```bash
# 啟動 Mock API
make up-mock

# 或直接用 docker compose
docker compose -f docker-compose.yml -f docker-compose.mock.yml up -d
```

---

## 📡 Mock API 端點

Mockoon 服務跑在 `http://localhost:3001`，所有端點前綴為 `/api`：

| 端點 | 方法 | 用途 |
|------|------|------|
| `/api/payment/credit-card` | POST | Mock 信用卡付款 |
| `/api/logistics/track/:trackingId` | GET | Mock 物流查詢 |
| `/api/sms/send` | POST | Mock 簡訊發送 |
| `/api/auth/google/callback` | GET | Mock Google OAuth |

### 測試範例

```bash
# 信用卡付款
curl -X POST http://localhost:3001/api/payment/credit-card \
  -H "Content-Type: application/json" \
  -d '{"amount": 1000, "cardNumber": "4111111111111111"}'

# 物流查詢
curl http://localhost:3001/api/logistics/track/TW-2026-001

# 簡訊發送
curl -X POST http://localhost:3001/api/sms/send \
  -H "Content-Type: application/json" \
  -d '{"phone": "+886912345678", "message": "測試簡訊"}'
```

---

## ✏️ 自訂 Mock 規則

### 方式 1：直接編輯 JSON（進階）

1. 編輯 [mockoon-data.json](./mockoon-data.json)
2. 驗證 JSON 格式：
   ```bash
   cat mocks/mockoon-data.json | python3 -m json.tool
   ```
3. 重啟容器：
   ```bash
   docker compose restart mock-server
   ```

### 方式 2：使用 Mockoon Desktop App（推薦）

1. 下載 [Mockoon](https://mockoon.com/download/)
2. 開啟本目錄的 `mockoon-data.json`
3. 視覺化編輯環境、路由、回應
4. 儲存後重啟容器

---

## 🆘 故障排除

### Q1: 容器啟動後立即退出
**原因**：`mockoon-data.json` 格式錯誤
**解決**：
```bash
# 驗證 JSON
python3 -m json.tool mocks/mockoon-data.json
```

### Q2: 無法連線到 Mock API
**檢查**：
```bash
# 容器狀態
docker compose ps mock-server

# 健康檢查
curl http://localhost:3001/health
```

### Q3: 修改 mockoon-data.json 後沒生效
**解決**：
```bash
docker compose restart mock-server
```

---

## 📚 參考資源

- [Mockoon 官方文檔](https://mockoon.com/docs/)
- [Mockoon CLI GitHub](https://github.com/mockoon/mockoon/tree/main/packages/cli)

---

## 📝 維護記錄

| 日期 | 版本 | 變更 |
|------|------|------|
| 2026-06-11 | 1.0 | 初版建立（README + mockoon-data.json） |
| 2026-06-29 | 1.1 | 移除已下線的 Local LLM 相關說明與連結 |
