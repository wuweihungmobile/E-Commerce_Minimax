package com.nextkey.ecommerce.shared.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sprint 286（DEF-330c）：{@code TenantContext.getCurrentTenant()} 的每一個呼叫處都要登記在
 * {@link #BASELINE}；數量跟登記的不一樣（不論增減）就會失敗。
 *
 * <p>為什麼要守：{@link TenantContext} class 開頭已經寫明——{@code getCurrentTenant()} 對「沒有加入
 * 任何店鋪」的使用者回傳的是系統租戶佔位值，不是 {@code null}，而且所有這類使用者共用同一個值。
 * 「拿呼叫者的租戶去查／去比對」對這類使用者等於「同租戶」，Sprint 232～234 連續在店家層端點找到
 * 四處因此外洩的案例（DEF-319／DEF-326），Sprint 286（DEF-330a）又在通知模板端點找到同根因的一個。
 *
 * <p>這道守門<b>不是</b>語意稽核——它不判斷「現有呼叫處使用 {@code getCurrentTenant()} 的方式對不對」
 * （150+ 處逐一查證是否已靠 {@link TenantContext#isStoreTenant} 或等效的 ownership 檢查守住，
 * 超出一次 Sprint 的合理範圍，詳見 SPRINT_286_PLAN.md 的複核記錄），它只負責「有新的呼叫處出現就攔下來」：
 * 新增一個店家層端點、或在既有檔案裡新增一次呼叫，都會讓對應檔案的計數對不上，逼著加的人／未來的我
 * 先評估一次系統租戶風險再把新數字登記進來，而不是悄悄漏過去、要等下一輪全庫掃描才被發現。
 *
 * <p>只數「真的呼叫」的那一行：trim 後以 {@code *} 或 {@code //} 開頭的行（Javadoc／註解裡提到這個
 * 方法名，例如 {@code OrderService.java} 裡解說系統租戶風險的註解段落）不算。{@link TenantContext}
 * 自己（方法宣告本身）排除在掃描之外，{@code .bak} 備份檔本來就不是 {@code *.java}，不會被掃到。
 */
@DisplayName("Sprint 286（DEF-330c）: TenantContext.getCurrentTenant() 呼叫處清點")
class TenantContextCallSiteDriftTest {

    private static final Path MAIN_JAVA =
            Path.of(System.getProperty("basedir", "."), "src", "main", "java");

    /** {@code getCurrentTenant()} 的宣告本身（不是呼叫處），掃描時排除。 */
    private static final String SELF = "com/nextkey/ecommerce/shared/tenant/TenantContext.java";

    private static final Pattern CALL = Pattern.compile("getCurrentTenant\\(\\)");

    /**
     * 檔案（相對 {@code backend/src/main/java}）→ 目前已知、經過人工確認的呼叫處數量。Sprint 286
     * 建立時依模組分組登記；各模組是否已有 {@code isStoreTenant}／ownership 等效防護，詳見
     * SPRINT_286_PLAN.md 與 DEFERRED_ITEMS_TRACKER.md 的 DEF-330(c) 複核記錄，這裡只負責鎖住數字。
     */
    private static final Map<String, Integer> BASELINE = new LinkedHashMap<>();

    static {
        // ERP 商家進銷存（Sprint 275/276 M16；端點全走 erp:* 權限，僅已確認有店鋪的賣家／平台可達）
        BASELINE.put("com/nextkey/ecommerce/api/controller/ErpController.java", 17);
        BASELINE.put("com/nextkey/ecommerce/core/erp/InventoryService.java", 3);
        BASELINE.put("com/nextkey/ecommerce/core/erp/PurchaseOrderService.java", 7);
        BASELINE.put("com/nextkey/ecommerce/core/erp/StockMovementService.java", 4);
        BASELINE.put("com/nextkey/ecommerce/core/erp/SupplierService.java", 5);

        // 通知模板（Sprint 128 DEF-073；list/detail 原用 isAuthenticated() 任何登入者皆可呼叫，
        // 已於 Sprint 286（DEF-330a）改回 notification_template:read，與既有的 render 端點一致）
        BASELINE.put("com/nextkey/ecommerce/api/controller/NotificationTemplateController.java", 6);

        // 店家內容管理模組：CMS/Post/Media（Sprint 129 DEF-075；cms:*/media:* 權限）
        BASELINE.put("com/nextkey/ecommerce/api/controller/PostController.java", 16);
        BASELINE.put("com/nextkey/ecommerce/core/cms/CmsService.java", 5);
        BASELINE.put("com/nextkey/ecommerce/core/media/MediaService.java", 15);

        // 店家內容管理模組：FAQ/知識庫（同為 Sprint 129 DEF-075；faq:*/knowledge:* 權限）
        BASELINE.put("com/nextkey/ecommerce/core/faq/FaqService.java", 16);
        BASELINE.put("com/nextkey/ecommerce/core/knowledge/KnowledgeBaseService.java", 18);

        // 賣家後台／分析
        BASELINE.put("com/nextkey/ecommerce/api/controller/SellerDashboardController.java", 3);
        BASELINE.put("com/nextkey/ecommerce/core/analytics/AnalyticsService.java", 5);

        // 運費模板（Sprint 286（DEF-330a）複核：4 個端點沿用 product:* 權限而非專屬權限，
        // list 端點與其餘 3 個 mutation 端點不同層級——BUYER 持有 product:read，可呼叫但只會查到
        // 「自己（系統租戶）名下」的空清單；改為專屬權限有造成 SELLER 角色退化的風險，已登記待評估，
        // 本輪未變更，見 DEFERRED_ITEMS_TRACKER.md DEF-330(a)）
        BASELINE.put("com/nextkey/ecommerce/api/controller/ShippingTemplateController.java", 4);

        // 客服工單（Sprint 91；support_ticket:* 權限，買家只能看自己建立的工單，由 Service 另行過濾）
        BASELINE.put("com/nextkey/ecommerce/api/controller/support/SupportTicketController.java", 4);

        BASELINE.put("com/nextkey/ecommerce/api/filter/RateLimitFilter.java", 1);

        // 訂房／訂單／房源／商品／定價核心網域
        BASELINE.put("com/nextkey/ecommerce/core/booking/BookingService.java", 4);
        BASELINE.put("com/nextkey/ecommerce/core/booking/RoomCalendarService.java", 1);
        BASELINE.put("com/nextkey/ecommerce/core/checkout/CombinedCheckoutService.java", 1);
        BASELINE.put("com/nextkey/ecommerce/core/order/OrderService.java", 3);
        BASELINE.put("com/nextkey/ecommerce/core/room/RoomService.java", 4);
        BASELINE.put("com/nextkey/ecommerce/core/product/ProductService.java", 4);
        BASELINE.put("com/nextkey/ecommerce/core/product/ProductSkuService.java", 1);
        // DEF-255/DEF-242（Sprint 185）已對帶 roomListingId/listingId 的分支補上 ownership 檢查；
        // 無篩選條件的分支查詢呼叫者「自己」的租戶，對沒有店鋪者會是系統租戶（目前無資料、查到空清單）
        BASELINE.put("com/nextkey/ecommerce/core/pricing/PricingService.java", 4);

        BASELINE.put("com/nextkey/ecommerce/core/feature/FeatureToggleService.java", 2);
        BASELINE.put("com/nextkey/ecommerce/core/idempotency/IdempotencyService.java", 1);
        BASELINE.put("com/nextkey/ecommerce/core/logistics/LogisticsService.java", 1);
        BASELINE.put("com/nextkey/ecommerce/core/returns/ReturnRequestService.java", 3);
        BASELINE.put("com/nextkey/ecommerce/core/review/BookingReviewService.java", 1);
        BASELINE.put("com/nextkey/ecommerce/core/review/ReviewReplyService.java", 1);
        BASELINE.put("com/nextkey/ecommerce/core/review/ReviewService.java", 2);

        // 結算／撥款（平台端操作，Sprint 196～262；isSuperAdmin 旗標另行流轉，與本檔案呼叫處無關）
        BASELINE.put("com/nextkey/ecommerce/core/settlement/SettlementGenerator.java", 2);
        BASELINE.put("com/nextkey/ecommerce/core/settlement/SettlementReviewer.java", 3);
        BASELINE.put("com/nextkey/ecommerce/core/settlement/TransferService.java", 2);
    }

    @Test
    @DisplayName("每個檔案目前的 getCurrentTenant() 呼叫處數量都跟登記的一致（增減都要人工確認後更新）")
    void callSitesMatchBaseline() throws IOException {
        Map<String, Integer> actual = scan();
        List<String> problems = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : BASELINE.entrySet()) {
            int expected = entry.getValue();
            int found = actual.getOrDefault(entry.getKey(), 0);
            if (found != expected) {
                problems.add(entry.getKey() + "：登記 " + expected + " 處，實際掃到 " + found + " 處");
            }
        }
        for (Map.Entry<String, Integer> entry : actual.entrySet()) {
            if (!BASELINE.containsKey(entry.getKey())) {
                problems.add(entry.getKey() + "：全新出現的呼叫處（" + entry.getValue() + " 處），未登記在 BASELINE");
            }
        }

        assertThat(problems)
                .as("TenantContext.getCurrentTenant() 的呼叫處有變動，請先評估新／變動的呼叫處是否需要"
                        + " TenantContext.isStoreTenant() 或等效的 ownership 檢查（見 class 開頭的系統租戶"
                        + "風險說明與 DEFERRED_ITEMS_TRACKER.md DEF-330(c)），再更新本測試的 BASELINE：\n%s",
                        String.join("\n", problems))
                .isEmpty();
    }

    @Test
    @DisplayName("守門本身有東西可守：掃描範圍內確實找得到呼叫處")
    void guardCoversSomething() throws IOException {
        assertThat(scan()).isNotEmpty();
    }

    private static Map<String, Integer> scan() throws IOException {
        Map<String, Integer> counts = new TreeMap<>();
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = MAIN_JAVA.relativize(file).toString().replace('\\', '/');
                if (relative.equals(SELF)) {
                    continue;
                }
                int count = 0;
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("*") || trimmed.startsWith("//")) {
                        continue;
                    }
                    if (CALL.matcher(line).find()) {
                        count++;
                    }
                }
                if (count > 0) {
                    counts.put(relative, count);
                }
            }
        }
        return counts;
    }
}
