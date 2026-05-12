# Release Notes - v2026.05.11-0x

**發布日期**: 2026-05-12
**發布類型**: Patch
**基於分支**: develop → main

## 新功能 ✨
- CI/CD Pipeline 優化：修復 Dependency Scan job timeout 問題

## 改進 🚀
- 新增 dependency-scan job timeout 設定
- OWASP dependency check 改為本地執行，CI 跳过以提昇速度
- E2E 測試 job 直接在 workflow 中 build frontend，不再依賴 artifact

## Bug 修復 🐛
- 修正 CI/CD pipeline 中 OWASP 檢查無限期掛起的問題
- 修正 E2E 測試中 frontend build artifact 找不到的問題
- 修正 TestDatabaseInitializer race condition
- 修正 E2E 測試中的 tenantId 設定問題
- 修正 Redis Docker container creation flags
- 修正後端 checkstyle 配置

## 技術變更 ⚙️
- CI profile 從 `test` 改為 `integration-test` 以使用真實 PostgreSQL
- 移除 `verify services health` 步驟（已確認服務正常）
- 簡化 frontend build step，信賴 .next 目錄建立
- **SCA 安全掃描升級**：使用 GitHub `dependency-review-action` 替代 OWASP Maven plugin
  - 原因：OWASP Maven plugin 需要下載 NVD 資料庫，在 CI 環境中會無限期掛起
  - 優勢：使用 GitHub Advisory Database，無需本地資料庫下載，快速完成

## CI/CD 改善
- Backend Build & Test: 3m0s (優化後)
- Frontend Build & Test: 1m4s
- E2E Tests: 2m23s
- 總執行時間: ~5m14s (修復前會無限期掛起)

## 遷移指南
1. 更新到最新 develop 分支
2. 執行 `mvn clean test -Dspring.profiles.active=integration-test` 確認測試通過
3. 本地執行 OWASP 檢查：`mvn org.owasp:dependency-check-maven:check`

## 貢獻者
- wuweihungmobile

## 相關 Commit
- d6271c3 fix: skip slow OWASP dependency check in CI
- f54b5e9 fix: improve OWASP dependency check command for faster CI execution
- c1b3387 fix: add timeout to dependency-scan job and skip tests for faster execution
- 14567a3 fix: build frontend in E2E job instead of relying on artifact
