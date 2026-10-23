# 正式環境第一次設定 admin / First Admin Setup

> **適用**：正式或預備環境的**新資料庫**（Flyway 已跑完 `V94`）
> **建立日期**：2026-10-24（Sprint 305，DEF-418）
> **相關**：[V94__Remove_Test_Seed_Data_And_Lock_Known_Accounts.sql](../../backend/src/main/resources/db/migration/V94__Remove_Test_Seed_Data_And_Lock_Known_Accounts.sql)、[scripts/sql/seed-e2e-admin.sql](../../scripts/sql/seed-e2e-admin.sql)（**只限 E2E 與本機 dev**）

## 1. 背景

Flyway 沒有環境過濾，所有遷移在每個資料庫都會跑。`V1`／`V5`／`V7`／`V15`／`V16` 原本把測試帳號與測試租戶放進每個資料庫，其中 `admin@nextkey.local`（`SUPER_ADMIN`）的密碼被 `V7` 蓋成已知的測試密碼 `Test123!`。

`V94`（Sprint 305，DEF-418）之後，新資料庫建好時：

| 項目 | 狀態 |
|---|---|
| `admin@nextkey.local` | **保留，但密碼是鎖住的**（不可登入）；`SUPER_ADMIN`、`ACTIVE` |
| 其他測試帳號、5 個測試租戶與其房源／房型／定價規則／貼文／成員 | **已刪除** |
| 系統租戶（`00000000-0000-0000-0000-000000000001`）與它的功能開關 | 保留 |

所以**新環境一開始沒有任何人能以 admin 身分登入**——需要由部署者設定第一個 admin 密碼，這就是本文件。

## 2. 設定步驟

> ⚠️ 不要執行 `scripts/sql/seed-e2e-admin.sql`：它把 admin 設成公開在 repo 裡的已知密碼 `Test123!`，只給 E2E 與本機 dev 用。

1. **準備新密碼**：用密碼管理器產生強密碼。不要寫進 repo、工單、聊天紀錄或 shell 歷史。
2. **產生 bcrypt 雜湊**（cost 10，與應用程式一致）。用互動式輸入，密碼不會出現在命令列：

   ```bash
   htpasswd -nBC 10 admin | cut -d: -f2
   ```

   `htpasswd` 會提示輸入兩次密碼，輸出形如 `$2y$10$…`（60 字元）。Spring 的 `BCryptPasswordEncoder` 接受 `$2y$` 前綴；若想與其他雜湊一致，可把 `$2y` 換成 `$2a`。（Linux 需安裝 `apache2-utils`。）
3. **更新資料庫**（把雜湊貼進單引號，**只改這一列**）：

   ```sql
   UPDATE users
      SET password_hash = '<上一步的 bcrypt 雜湊>'
    WHERE email = 'admin@nextkey.local';
   -- 預期：UPDATE 1
   ```

   建議同時把 email 改成真實可收信的信箱，忘記密碼流程才收得到信（`admin@nextkey.local` 不是可收信的網域）：

   ```sql
   UPDATE users SET email = '<營運信箱>' WHERE email = 'admin@nextkey.local';
   ```

   之後的指令與文件裡的 `admin@nextkey.local` 請換成新的 email。
4. **驗證**：以新密碼登入 `POST /api/v2/auth/login`，應回 `200`；以 `Test123!` 登入應回 `401`。
5. **善後**：建議再建立個人的管理員帳號、停用共用的 `admin@nextkey.local`。

## 3. 新環境第一次部署後的檢查

遷移完成後、設定 admin 密碼之前，下面兩個查詢都應回 `0`；用 `Test123!` 登入 admin 應回 `401`：

```sql
SELECT count(*) FROM users   WHERE email <> 'admin@nextkey.local';
SELECT count(*) FROM tenants WHERE id    <> '00000000-0000-0000-0000-000000000001';
```

`make validate-e2e` 每次都會對 Flyway 從零建出的乾淨資料庫自動做同樣的檢查（`scripts/validate-e2e.sh` 的 3.5 步），再執行 `seed-e2e-admin.sql` 讓 E2E 能登入。

## 4. 舊資料庫（已套用過舊遷移、有活動紀錄）

`V94` 會先把 admin 與全部測試帳號的密碼鎖住，再逐個帳號、逐個租戶嘗試刪除。若某個測試帳號或租戶仍被其他資料參照（外鍵擋下），該列**保留**並在日誌印出 `NOTICE: V94：仍有其他資料參照，保留…`，遷移不會失敗。這種情況下**密碼仍然是鎖住的**；要不要手動清理那些殘留資料由資料庫的擁有者決定。

## 5. 限制

- 鎖密碼不會讓**已簽發**的 access token 與 refresh token 失效（access token 最長 15 分鐘；refresh token 存在 Redis，到期為止）。新資料庫沒有這個問題；對舊資料庫，若 admin 曾登入過，可視需要手動撤銷。
- `htpasswd` 產生的雜湊已用 Spring Security 6.2.4 的 `BCryptPasswordEncoder` 驗證過（Sprint 305）：`$2y$10$…`（60 字元）原樣可通過、換成 `$2a` 也可通過、錯誤密碼不通過。驗證用的是批次模式 `htpasswd -nbBC 10`＋拋棄式探測密碼；本文步驟 2 的互動式輸入只差在密碼來源，沒有另外實測。
