# Sprint 122 Plan — DEF-052：修正 `-Pintegration-test` 誤導性文件引用

**Sprint**: Sprint 122
**日期**: 2026-09-04
**主題**: 使用者拍板選擇的文件修正項，1 SP。

---

## 1. 缺陷

`-Pintegration-test` 是一個**不存在的 Maven profile**。failsafe plugin 其實無條件綁在
`verify` 生命週期階段上，`-P` 旗標對它毫無作用（Maven 對不存在的 profile 只會發出警告，
不報錯）。三處文件把它與 Spring 的 `@ActiveProfiles("integration-test")` 混為一談，
兩者毫無關係：`Makefile:102` 的 `test-db-up` 提示文字、`backend/pom.xml` 的 surefire／
failsafe 註解、`docs/08_deployment/LOCAL_CI_VALIDATION.md` 的 `test-db-up` 說明表格。

無功能影響，純屬會誤導讀者的文字。

## 2. 查證：三處以外的匹配都是假陽性

依 DEF-052 記錄的前置需求「改 3 處文字：Makefile 說明、pom 註解、以及日後任何引用」，
全庫掃描 `Pintegration-test`／`integration-test profile` 字樣，命中約 80 個檔案。逐一分類：

- **歷史 Sprint 紀錄**（`SPRINT_*_REVIEW.md`／`*_RETRO.md`／`*_TASKS.md`／
  `RELEASE_NOTES_*.md`／`FLYWAY_EVALUATION.md`）：是當時實際執行過的指令與觀測的時間點
  快照，依 Rule 3（精準改動）**不回頭改寫**——改寫會抹去「這個誤解曾經存在過」的事實
  本身，且與 DEF-049 v1.9 記錄「S100~S103 測試數偏高但刻意不回頭改寫歷史 row」同一個
  理由。
- **`docker-compose.test.yml`／`scripts/validate-schema.sh`**：查證後這兩處的
  「integration-test profile」講的是**真實存在的 Spring profile**
  （`SPRING_PROFILES_ACTIVE=integration-test`／`@ActiveProfiles("integration-test")`），
  不是本缺陷指的 Maven `-P` 旗標，**非 DEF-052 的實例**，未誤觸。

## 3. 修法

三處文字各自加註「為何是誤導」而非只是刪字，避免下一個人重新引入同樣的誤解：

1. `Makefile:102` — `mvn verify -Pintegration-test` 改為 `mvn verify`，加註
   「failsafe 無條件跑在 verify 階段，非靠 -P 觸發」。
2. `backend/pom.xml` — surefire 排除區塊與 failsafe 綁定區塊的註解各自改寫，
   明確寫出「`-Pintegration-test` 不是一個存在的 profile」並回指 DEF-052。
3. `docs/08_deployment/LOCAL_CI_VALIDATION.md` — `test-db-up`/`test-db-down` 說明
   表格同步修正，理由同上。

## 4. 範圍外

- 歷史 Sprint 記錄與 `FLYWAY_EVALUATION.md`：時間點快照，不回頭改寫（見 §2）。
- `docker-compose.test.yml`／`scripts/validate-schema.sh`：查證後為真實 Spring profile
  引用，非本缺陷範圍，未誤觸修改。

## 5. 驗證

| # | 內容 | 結果 |
|---|------|------|
| ① | `xmllint --noout backend/pom.xml` | XML 格式正確 |
| ② | `rm -rf backend/target/maven-status && mvn -o compile`（比照 S116 教訓避免假成功） | BUILD SUCCESS |
| ③ | `grep -rn "Pintegration-test" Makefile backend/pom.xml docs/08_deployment/LOCAL_CI_VALIDATION.md` | 僅剩「不是存在的 profile」的說明性引用，無誤導性指令範例 |

ℹ️ **本輪未跑 `mvn -o verify` 全量回歸**：純註解／文件變更，不影響任何被測試的生產或
測試程式碼行為，比照 Rule 3（精準改動）與 Rule 6（token 預算）不做超出需求的驗證；
編譯成功即足以確認 `pom.xml` 未被改壞。commit／push 由既有 pre-commit／pre-push hook
的既定驗證把關。
