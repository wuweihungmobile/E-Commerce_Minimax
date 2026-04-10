# AISDLC_INIT_改善_01.md

根據您提供的 `AISDLC_INIT.md` 以及 `tools/README.md` 文件，配合實際執行的測試分析，以下是本框架在專案初始化階段遇到的所有「窒礙難行之處」與「待改善建議」。

---

## 🛠️ 第一部分：`AISDLC_INIT.md` 文件指引的缺失與錯誤

### 1. 遺漏最關鍵的「取得來源檔案 (Git Clone)」前置步驟
**窒礙難行之處：**
作為框架入口文件，`AISDLC_INIT.md` 內的自動/手動初始化皆預設開發者本地已有 `/path/to/AISDLC_ALL/...`。雖然在另一個隱密的 `tools/README.md` 中有教大家如何 clone（包含私有庫需要用 SSH），但許多開發者第一線只看 `AISDLC_INIT.md`，這會導致他們不知道去哪裡生出源碼。
**改善建議：**
應在文件中加入明確的**前置作業**說明，教導開發者如何從 GitHub 取得這包框架源碼（並提醒私有庫需攜帶 SSH 或 PAT Token）。

### 2. 要求複製不存在的舊版本配置檔
**窒礙難行之處：**
在手動初始化「步驟 3」中，文件要求執行以下指令：
`cp AISDLC/framework/guides/user/onboarding/templates/AISDLC_PROJECT_CONFIG_Template.md ./AISDLC_PROJECT_CONFIG.md`
然而，我們實際搜索 v0.09 框架目錄後發現，該檔案**已經不復存在**（可能是被遺留在舊版中或被精簡掉了）。執行這行指令必定報錯。
修正這行指令，改為拷貝目前 v0.09 實際負責管理設定的 `CLAUDE.md` 及 `.claude/skills` 資料夾，刪除關於 `AISDLC_PROJECT_CONFIG_Template.md` 的描述。

### 3. `tools/README.md` 提供的一鍵下載指令 (curl) 對私有庫完全無效
**窒礙難行之處：**
在 `tools/README.md` 的安裝「方法 1」與「方法 2」中，教導開發者使用 `curl -fsSL https://raw.githubusercontent.com/.../init_project.sh` 來抓取腳本。但由於目標是**私有庫 (Private Repo)**，未帶任何授權標頭的 `curl` 指令會被 GitHub 直接阻擋（回傳 404 Not Found 版的空殼檔案），導致連「下載腳本」這第一步都絕對無法成功。
**改善建議：**
如果要支援私有庫的遠端腳本拉取，`README.md` 中的 `curl` 範例必須加上如何掛載 PAT 的說明：
`curl -H "Authorization: token YOUR_PAT" -fsSL https://...`

---

## 💣 第二部分：執行腳本 (`init_project.sh`) 設計上的致命缺陷

即使開發者按照 `tools/README.md` 的教學，把專案 clone 到了本地端，這支 `init_project.sh` 腳本的設計邏輯依舊很容易導致執行失敗，或者進行不必要的冗餘操作。

### 1. 強制二次重新下載 (Hardcoded Clone) 的邏輯衝突
**窒礙難行之處：**
`init_project.sh` 的腳本內部寫死了：**只要一執行，就必然會去 GitHub 上 `git clone` 專案到一個暫存資料夾 (mktemp)**。
這意味著：開發者明明已經 `git clone` 了一大包 60MB 以上的原始碼到本地，結果執行裡面的腳本時，腳本又會「無視本地檔案」，再次連上網路用 `git clone --depth 1` 抓同樣的東西！

### 2. 授權方式的不兼容（HTTPS 與 SSH 衝突）
**窒礙難行之處：**
因為腳本強制重新下載，它預設是使用公開的 HTTPS 網址。如果不加 `--ssh` 參數，連線私有庫會直接報錯 `404/Permission Denied` 中斷。
但如果開發者本身是使用 Personal Access Token (PAT) 抓下初始檔案，並沒有掛載 SSH Key，那他就算加了 `--ssh` 依然會報錯無法下載！這讓腳本成為一個巨大的地雷。

**✨ `init_project.sh` 改善建議：**
修改 Shell Script 的邏輯，讓它變得更聰明。
1. **加入「在地模式 (Local Run)」檢測**：腳本啟動時，先檢查所在層級是否就已經是完整的框架資料夾（如檢查有沒有 `agent/`, `docs_template/`, `CLAUDE.md` 等資料）。
2. **條件式跳過連網下載**：
   - 如果偵測到必要檔案早就在身邊 👉 **跳過 `git clone` 階段**，直接進入建立 `docs/` 資料夾流程。
   - 如果偵測為 curl 遠端安裝模式 👉 才執行原有的 `git clone` 邏輯。
3. **加強私有庫 PAT Token 支援選項**：除了 `--ssh`，可考慮加入 `--token YOUR_TOKEN` 參數直接把 Token 帶入腳本供臨時拉取使用。

---

## 📝 總結建議的正確初始化流程

如果要不修改腳本而順利完成引入目前的 v0.09 版，現在我唯一推薦的正確做法是避開有缺陷的自動腳本，改為「**全手動初始化**」。我也正是用這個方法成功且乾淨地將框架配置到我們專案中的：

1. 開發者自行利用 PAT Token 或 SSH 從 GitHub 將框架 `git clone` 下來（例如放在 `/Users/wuweihong/Cursor_Project/AISDLC`）。
2. 建立專案需要的 `docs` 目錄 (如 `mkdir -p docs/{01_requirements,02_architecture, ...}`)。
3. 把 `AISDLC/framework` 軟連結 (symlink) 指向我們剛下載的本地框架夾。
4. **最後一定要記得**：手動把 `.claude/skills` 與 `CLAUDE.md` 複製到專案根目錄，這樣 Cursor 才能正確開啟專案能力。
