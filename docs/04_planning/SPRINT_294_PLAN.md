# Sprint 294 Plan — `API_M17` 的 403 條件逐一對照 service 校正，並補兩個授權邊界單元測試

## 1. 起點與範圍

### 1.1 起點

[Sprint 292](SPRINT_292_PLAN.md) §6.4 與 `API_M17_Tenant.md` 修訂註記 7 都明寫了一個限制：只核對了 §4.6 一處，其餘 7 處同款「非該租戶成員」列「**未逐一核對**對應的 service 檢查是店主還是成員」。Sprint 293 的 push 後我在等雲端 CI（約 10～12 分鐘），這段時間把這個限制收掉。

對使用者指示的解讀（**推論，不是使用者說的**）：使用者說「請依序多執行幾個項目」，這是承接 Sprint 292 自己承認未做完的對照；不涉產品決策、只動文件與測試，生產程式碼零變更。

### 1.2 成功標準（開工時定義）

1. 文件中每一處「非該租戶成員」「且限該租戶成員」都能對上 `TenantService` 實際的檢查（成員 `ACTIVE` 或店主 `STORE_OWNER`），不符者改成如實。
2. 若發現某個授權邊界沒有任何測試鎖住，補測試；測試必須能失敗——以突變驗證，並要求「拿掉守門」的突變的失敗集合只含新測試（否則無法證明這個缺口先前存在）。
3. `TenantServiceTest` 全過、Checkstyle（測試）0 違規、`git diff -- backend/src/main` 為空。
4. 誠實揭露：沒驗證的、解讀的、可能過度宣稱的部分。

## 2. 對照結果

逐一讀 `TenantService.java` 各方法開頭的守門（行號為撰寫當下），對照 `API_M17_Tenant.md` 各節的「權限」行與失敗列：

| 文件小節 | 端點 | service 方法（行） | 實際檢查 | 文件原寫 | 處置 |
|---|---|---|---|---|---|
| §4.4 | `PUT /v2/tenants/{id}` | `updateTenant`（:319） | **店主**（`STORE_OWNER`） | 「且限該租戶成員」／「非該租戶成員」 | 校正 |
| §4.5 | `GET /v2/dashboard/tenants/features` | `getFeatureToggles`（:396） | 有效成員（`ACTIVE`） | 「非該租戶成員」 | 正確，未動 |
| §4.6 | `PUT /v2/dashboard/tenants/features/{feature}` | `updateFeatureToggle`（:506） | **店主** | （Sprint 292 已校正） | — |
| §4.7 | `GET /v2/tenants/{id}/members` | `getTenantMembers`（:721） | 有效成員（`ACTIVE`） | 「且限該租戶成員」／「非該租戶成員」 | 正確，未動 |
| §4.8 | `POST .../members/invite` | `inviteMember`（:753） | **店主** | 同 §4.4 | 校正 |
| §4.9 | `GET .../members/lookup` | `lookupMemberCandidateByEmail`（:811） | **店主** | 同 §4.4 | 校正 |
| §4.13 | `PUT .../members/{userId}/role` | `updateMemberRole`（:956） | **店主** | 同 §4.4 | 校正 |
| §4.14 | `DELETE .../members/{userId}` | `removeMember`（:1013） | **店主** | 同 §4.4 | 校正 |

另外 `E-4031` 總表（原寫「非該租戶成員，或嘗試變更店主角色/移除自己」）改為依端點類別說明。五個寫入類方法與 §4.6 都拋 `E_4031`，錯誤碼與訊息文字沒有出入，只是「條件」描述不精確。

`API_M17` 升 v2.2，新增修訂註記 8，並把註記 7 的「未逐一核對」補上「Sprint 294 已逐一核對」。

## 3. 測試缺口（對照過程中發現）

| 方法 | 「非店主被擋」的測試 |
|---|---|
| `updateTenant` | `updateTenant_staffRole_shouldThrowException` |
| `updateFeatureToggle` | `updateFeatureToggle_notOwner_rejectedWithoutAudit` |
| `inviteMember` | `inviteMember_notOwner_throwsE4031` |
| `lookupMemberCandidateByEmail` | `lookupMemberCandidateByEmail_notOwner_throwsE4031` |
| **`updateMemberRole`** | **無**（`TenantServiceTest` 只有成功、併發、目標是店主、新角色不合法四類；`TenantMemberInviteE2ETest`／`StoreMemberRevocationE2ETest` 也沒有非店主呼叫的案例） |
| **`removeMember`** | **無**（同上） |

也就是 Sprint 281（DEF-331(f)）修的那種缺陷——店主檢查寫成成員檢查——在這兩個方法上若再發生，沒有測試會發現。

## 4. 新增測試

在 `TenantServiceTest.MemberInviteTests` 新增兩個案例，比照同檔既有 `getTenantMembers_nonActiveMember_rejected` 的寫法（取出 `BusinessException` 並斷言 `ErrorCode.E_4031`）：

- `removeMember_activeStaffNotOwner_throwsE4031`
- `updateMemberRole_activeStaffNotOwner_throwsE4031`

設計重點（為什麼這樣寫，而不只是「丟例外」）：

- 呼叫者用獨立的 `STAFF_ID`，並用 `lenient()` 把「是有效（`ACTIVE`）成員」設為成立、同時「是店主」為不成立。這是 Sprint 281 修掉的缺陷形狀：若守門退化成只看成員，STAFF 會通過並繼續往下，測試必須失敗。
- 斷言錯誤碼 `E_4031`（不只是「有丟 `BusinessException`」，後者連「後面別的檢查丟出來的例外」都會通過）。
- `verify(never())`：沒有讀取目標成員（`findByTenantIdAndUserId`）、沒有儲存、沒有稽核紀錄——非授權者不應觸碰任何資料，也不該藉由後續錯誤碼探測目標成員是否存在。

## 5. 驗證

| 檢查 | 結果 |
|---|---|
| `mvn -o test -Dtest='TenantServiceTest*'` | 59 個全過、0 失敗；`MemberInviteTests` 30 個案例（surefire 報告確認兩個新案例存在且通過） |
| `mvn -o checkstyle:check@checkstyle-test` | 0 違規 |
| 生產程式碼 | `git diff -- backend/src/main` 為空 |

**突變驗證**（驅動腳本在 session 暫存目錄，未進 repo；每個突變「備份→套用→等 class→跑測試→還原」，還原後逐位元組一致，sha256 相同）：

| 突變 | 內容 | 結果 | 失敗集合 |
|---|---|---|---|
| M1 | `removeMember`：店主檢查改為成員（`ACTIVE`）檢查（Sprint 281 的缺陷形狀） | KILLED | 新測試＋5 個既有 `removeMember_*`（推斷原因：既有案例 stub 的是原本的店主檢查，突變後改呼叫另一個 repository 方法，回預設 false，成功路徑因此改丟 `E_4031`；**沒有逐一讀各案例的錯誤訊息確認**） |
| M2 | `removeMember`：呼叫 repository 但**忽略結果**（`... && false`） | KILLED | **只有新測試** |
| M3 | `updateMemberRole`：同 M1 | KILLED | 新測試＋5 個既有 `updateMemberRole_*`（同 M1 的推斷） |
| M4 | `updateMemberRole`：同 M2 | KILLED | **只有新測試** |

M2／M4 的「只有新測試」是這輪最有說服力的證據：它證明在此之前，拿掉這兩個守門**沒有任何測試會紅**。

**我的突變寫法出過一次錯**：第一版 M2／M4 寫成 `if (false && !repo.exists…)`，短路讓 repository 根本沒被呼叫，結果既有的 5 個 `removeMember_*`（M4 則是 5 個 `updateMemberRole_*`）也跟著紅燈。我推斷是 Mockito 嚴格模式的 unnecessary stubbing（既有案例 stub 了店主檢查，卻因短路從未被呼叫；守門被關掉本身不會讓這些成功路徑案例失敗，所以這是唯一說得通的解釋），但**沒有讀它們的錯誤訊息確認**。無論原因為何，紅燈來自突變寫法而不是「守門被拿掉」，不能當證據。改為「照常呼叫、忽略結果」後重跑，失敗集合才縮到只剩新測試。第一版的 M2／M4 結果作廢，不計入上表。

| 項目 | 結果 |
|---|---|
| pre-commit（Checkstyle＋Maven 編譯＋核心測試） | commit 後回填 |
| 雲端 CI | push 後回填 |

## 6. 限制（誠實揭露）

1. **只有 Mockito 單元測試**：沒有在真實 HTTP 層驗證這兩個端點對非店主回 403 `E-4031`；`@PreAuthorize` 先擋掉哪些全域角色、`ROLE_ADMIN` 通過註解後在 service 層的實際結果（service 只認該租戶店主）**都沒驗證**。文件的修訂註記 8 也如實寫了這一點。
2. **沒跑全量 `mvn verify`**：這輪只新增測試、生產程式碼零變更，依既有慣例（[[sprint-full-regression-policy]]）跑相關測試類別；全量由 pre-commit 的核心測試與雲端 CI 擔任。
3. **`inviteMember_notOwner_throwsE4031` 的斷言偏弱未動**：它只斷言丟 `BusinessException`，名字說 `E4031` 卻不檢查錯誤碼。這是既有測試，Rule 3 不順手改；若之後要加強是另一件事。
4. 文件行號（`:319`、`:396` …）是撰寫當下的 `TenantService.java`，之後會漂移；文件註記裡不寫行號，計畫書才有。
5. IDE 的 Java 語言服務在我編輯測試檔後 1 秒內就預先編譯，所以 Maven 這次沒印 `Compiling`；正式的 Maven 編譯與 Checkstyle 在 pre-commit 發生。

## 7. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 一句話推翻 |
|---|---|---|---|
| 1 | 補兩個單元測試，而不是只登記成 DEF | 不需產品決策、改動小、有同型前例可照寫；登記成 DEF 只會增加使用者要看的清單 | `git revert` 這個 commit 的測試檔部分 |
| 2 | 只補單元測試，不補 E2E | 要驗證的是 service 層檢查，E2E 需要真實使用者與租戶種子資料，成本高、對這個問題的邊際價值低 | 想補 E2E：另開 Sprint |
| 3 | 不動既有的 `inviteMember_notOwner_throwsE4031` | Rule 3 | — |

## 8. 需要使用者決定（沿用 Sprint 292 §8，無新增）

`DEF-397`（建議修訂 PRD §16.4.1 措辭）、`DEF-398`（前端 99 筆 ESLint warning，建議先做行為不變的 58 筆）、`DEF-399`（文件結構性漂移守門，建議做 (a)＋(b)）——回「依照建議」即照做。

## 9. 文件與程式異動清單

| 檔案 | 異動 |
|---|---|
| `docs/02_architecture/api/API_M17_Tenant.md` | v2.1→**v2.2**：§4.4／§4.8／§4.9／§4.13／§4.14 的權限行與失敗列、`E-4031` 總表、修訂註記 7（補上已核對）與 8 |
| `backend/src/test/java/com/nextkey/ecommerce/core/tenant/TenantServiceTest.java` | `MemberInviteTests` 新增 `STAFF_ID` 常數與 2 個案例 |
| `docs/04_planning/RELEASE_TRACKER.md` | v4.11→**v4.12**：新增 Sprint 294 row；回填 Sprint 293 的 push 狀態與雲端 CI |
| `docs/04_planning/SPRINT_293_PLAN.md` | §8 回填雲端 CI 結果 |
| `docs/04_planning/SPRINT_294_PLAN.md` | 新增（本文件） |

生產程式碼（`backend/src/main`）零變更。
