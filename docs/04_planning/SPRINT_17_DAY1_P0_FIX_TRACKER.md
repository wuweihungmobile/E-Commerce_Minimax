# Sprint 17 US-001 Day 1 下午 P0 修復追蹤

> **US ID**: US-001
> **Sprint**: Sprint 17 (2026-06-08 ~ 2026-06-19)
> **Day 1 下午 (2026-06-08 13:00-18:00)**
> **分支**: `feature/US-001-fix-test-bugs`
> **基於**: [SPRINT_17_TEST_BUG_ROOT_CAUSE_ANALYSIS.md](./SPRINT_17_TEST_BUG_ROOT_CAUSE_ANALYSIS.md)

---

## 1. Day 1 下午 P0 修復目標

| 根因 | 測試類別 | 錯誤數 | 預估 SP | 預定完成時間 |
|------|---------|--------|---------|------------|
| RC-1 | M07PaymentMockIntegrationTest | 8 | 0.5 SP | 14:00-15:00 |
| RC-2 | M18KnowledgePhase2IntegrationTest | 9 | 0.2 SP | 15:00-15:30 |
| RC-3 | M18MediaIntegrationTest | 12 | 0.5 SP | 15:30-17:00 |
| | **合計** | **29** | **1.2 SP** | **13:00-17:00** |

**Day 1 EOD 預期**: 109 → 80 個 bug（-29 個）

---

## 2. 修復進度

### RC-1: M07PaymentMockIntegrationTest (8 個)

- [x] ⏳ 確認當前失敗狀態 (8 個 errors, `setUp:101 doNothing() for non-void`)
- [ ] 修復 setUp:101
- [ ] 跑 `mvn test -Dtest=M07PaymentMockIntegrationTest` 驗證
- [ ] 更新進度

### RC-2: M18KnowledgePhase2IntegrationTest (9 個)

- [ ] 確認當前失敗狀態
- [ ] 加上 `@MockBean JwtTokenService`
- [ ] 跑 `mvn test -Dtest=M18KnowledgePhase2IntegrationTest` 驗證
- [ ] 更新進度

### RC-3: M18MediaIntegrationTest (12 個)

- [ ] 確認當前失敗狀態
- [ ] 修復 HTTP 200→201 等 status 預期
- [ ] 跑 `mvn test -Dtest=M18MediaIntegrationTest` 驗證
- [ ] 更新進度

---

## 3. Day 1 EOD 驗證

- [ ] 跑完整 `mvn test` 確認當日進度
- [ ] 統計剩餘失敗數（預期 ≤ 80 個）
- [ ] 更新 SPRINT_17_TEST_BUG_ROOT_CAUSE_ANALYSIS.md
- [ ] 提交 Day 1 修復到 feature 分支

---

**文件版本**: v1.0
**最後更新**: 2026-06-08 (Sprint 17 Day 1 下午)
**作者**: Claude Code (AI Assistant)
