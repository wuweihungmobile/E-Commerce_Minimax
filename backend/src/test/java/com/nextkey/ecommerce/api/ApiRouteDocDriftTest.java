package com.nextkey.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.nextkey.ecommerce.api.controller.AnalyticsController;
import com.nextkey.ecommerce.api.controller.AuthController;
import com.nextkey.ecommerce.api.controller.BookingController;
import com.nextkey.ecommerce.api.controller.BookingPaymentController;
import com.nextkey.ecommerce.api.controller.BookingReviewController;
import com.nextkey.ecommerce.api.controller.CartController;
import com.nextkey.ecommerce.api.controller.ChatController;
import com.nextkey.ecommerce.api.controller.CheckoutController;
import com.nextkey.ecommerce.api.controller.CmsController;
import com.nextkey.ecommerce.api.controller.DashboardBookingController;
import com.nextkey.ecommerce.api.controller.DashboardListingController;
import com.nextkey.ecommerce.api.controller.DashboardPricingController;
import com.nextkey.ecommerce.api.controller.ErpController;
import com.nextkey.ecommerce.api.controller.ListingController;
import com.nextkey.ecommerce.api.controller.LogisticsController;
import com.nextkey.ecommerce.api.controller.MediaCategoryController;
import com.nextkey.ecommerce.api.controller.MediaController;
import com.nextkey.ecommerce.api.controller.NotificationController;
import com.nextkey.ecommerce.api.controller.NotificationPreferenceController;
import com.nextkey.ecommerce.api.controller.NotificationTemplateController;
import com.nextkey.ecommerce.api.controller.OAuthController;
import com.nextkey.ecommerce.api.controller.OrderController;
import com.nextkey.ecommerce.api.controller.OrderPaymentController;
import com.nextkey.ecommerce.api.controller.PostController;
import com.nextkey.ecommerce.api.controller.PricingController;
import com.nextkey.ecommerce.api.controller.ProductController;
import com.nextkey.ecommerce.api.controller.PublicListingController;
import com.nextkey.ecommerce.api.controller.ReviewController;
import com.nextkey.ecommerce.api.controller.RoomCalendarController;
import com.nextkey.ecommerce.api.controller.RoomController;
import com.nextkey.ecommerce.api.controller.SellerDashboardController;
import com.nextkey.ecommerce.api.controller.ShippingTemplateController;
import com.nextkey.ecommerce.api.controller.StorefrontController;
import com.nextkey.ecommerce.api.controller.TenantController;
import com.nextkey.ecommerce.api.controller.returns.ReturnRequestController;
import com.nextkey.ecommerce.api.controller.settlement.SettlementController;
import com.nextkey.ecommerce.api.controller.settlement.TransferController;
import com.nextkey.ecommerce.api.controller.support.SupportTicketController;

/**
 * Sprint 241：API 規格文件記載的路由，必須與 Controller 一致（路由漏記、記了不存在的路由都會失敗）。
 *
 * <p>為什麼要守：Sprint 232 的文件一致性檢查發現，API 規格與實作長期漂移——訂房付款 4 個端點、商家端訂房列表、
 * 購物車的清空／件數／優惠券端點都存在了好幾個 Sprint，規格文件完全沒有；規格卻列了從未存在的 {@code GET /cart/items}。
 * 人工雙處記帳的文件遲早再漂移（同 {@code ErrorCodeDocDriftTest} 的理由），所以用機械比對守住「端點有沒有記載」。
 *
 * <p>只守「路由（方法＋路徑）」：請求與回應的欄位形狀、錯誤碼的細節仍要人工維護（錯誤碼對照表另有
 * {@code ErrorCodeDocDriftTest}）。要把新的 API 規格納入守門，在 {@link #GUARDED} 登記文件與它描述的 Controller。
 *
 * <p>文件的寫法：每個端點要有一個「宣告行」，兩種寫法擇一——
 * <ul>
 *   <li>章節標題：章節編號後面<b>緊接</b>反引號包住的路由，例如 {@code ### 4.1 `GET /v2/cart` — 取得購物車}；</li>
 *   <li>{@code - **端點**: `POST /api/v2/auth/register`}（M03 的寫法）。</li>
 * </ul>
 * 路徑前面可以有 {@code /api}、後面可以接 {@code ?查詢參數}；路徑變數的名稱要與 Controller 相同。
 * <b>只有宣告行算數</b>：總覽表格、追蹤性表格、資料模型標題（例如 {@code #### ApplyPromoRequest — `POST …`}）裡提到路由，
 * 不能代替端點自己的章節。
 */
@DisplayName("Sprint 241: API 規格文件的路由與 Controller 一致")
class ApiRouteDocDriftTest {

    private static final Path DOCS = Path.of(System.getProperty("basedir", "."), "..", "docs", "02_architecture");

    /** 文件（相對 docs/02_architecture）→ 它描述的 Controller。 */
    private static final Map<String, List<Class<?>>> GUARDED = new LinkedHashMap<>();

    static {
        GUARDED.put("api/API_M03_Auth.md", List.of(AuthController.class, OAuthController.class));
        GUARDED.put("API_M04_Cart.md", List.of(CartController.class));
        // Sprint 243：M05 訂單（含合併結帳與整組付款端點；訂房的付款狀態路由在 OrderPaymentController，所以由 M05 宣告）、M06 訂房
        GUARDED.put("api/API_M05_Order.md",
                List.of(OrderController.class, CheckoutController.class, OrderPaymentController.class));
        GUARDED.put("API_M06_Booking.md",
                List.of(BookingController.class, BookingPaymentController.class, DashboardBookingController.class));
        // Sprint 262：M07 結算與撥款，文件先前完全不存在（DEF-364），新建後一併納入守門
        GUARDED.put("api/API_M07_Settlement.md",
                List.of(SettlementController.class, TransferController.class));
        // Sprint 263：M08 評價與社交互動，文件先前完全不存在（DEF-365），新建後一併納入守門
        GUARDED.put("api/API_M08_Review.md",
                List.of(ReviewController.class, BookingReviewController.class));
        // Sprint 265：M05 退貨申請，文件先前完全不存在（DEF-366），新建後一併納入守門
        GUARDED.put("api/API_M05_Return.md", List.of(ReturnRequestController.class));
        // Sprint 265：M18 客服工單，文件先前完全不存在（DEF-367），新建後一併納入守門
        GUARDED.put("api/API_M18_Support.md", List.of(SupportTicketController.class));
        // Sprint 266：M12 動態定價，v1.0 與實作落差過大全面改寫（DEF-368），改寫後一併納入守門
        GUARDED.put("api/API_M12_Dynamic_Pricing.md",
                List.of(PricingController.class, DashboardPricingController.class));
        // Sprint 266：M17 租戶管理，v1.1 與實作落差過大全面改寫（DEF-369）。AdminController 混雜大量
        // M17 以外端點（使用者/系統設定/稽核/採購單），無法整個類別納入守門，故僅登記 TenantController
        // 與 StorefrontController；AdminController 的租戶端點暫無機械防漂移，見文件 §7 已知限制
        GUARDED.put("api/API_M17_Tenant.md",
                List.of(TenantController.class, StorefrontController.class));
        // Sprint 272：M09 訊息通知中心，文件先前完全不存在（DEF-286 範圍），新建後一併納入守門
        GUARDED.put("api/API_M09_Notification.md", List.of(
                NotificationController.class, NotificationPreferenceController.class,
                NotificationTemplateController.class));
        // Sprint 273：M01 商品中心，v1.0 與實作落差過大全面改寫（DEF-286／DEF-372 範圍）。
        // ListingController／PublicListingController 是 PRODUCT／ROOM 共用的跨類型瀏覽端點，
        // 因只能整個類別歸屬一份文件，依編號順序登記在 M01（M02 文件會交叉引用）
        GUARDED.put("api/API_M01_Product_Center.md", List.of(
                ListingController.class, PublicListingController.class, ProductController.class));
        // Sprint 273：M02 房源中心，v1.0 與實作落差過大全面改寫（DEF-286／DEF-372 範圍）
        GUARDED.put("api/API_M02_Listing_Center.md",
                List.of(RoomController.class, RoomCalendarController.class));
        // Sprint 273：統一 Listing 建立端點（PRODUCT／ROOM 共用，DashboardListingController 僅此一條路由），
        // 獨立成文件校正內容，一併納入守門（DEF-286／DEF-372 範圍）
        GUARDED.put("api/API_Listing_Unified_Create.md", List.of(DashboardListingController.class));
        // Sprint 274：M10 即時通訊，文件先前完全不存在（DEF-286 範圍），新建後一併納入守門
        GUARDED.put("api/API_M10_Chat.md", List.of(ChatController.class));
        // Sprint 274：M11 物流追蹤，文件先前完全不存在（DEF-286 範圍），新建後一併納入守門
        GUARDED.put("api/API_M11_Logistics.md",
                List.of(LogisticsController.class, ShippingTemplateController.class));
        // Sprint 275：M13 商家工作台，文件先前完全不存在（DEF-286 範圍），新建後一併納入守門
        GUARDED.put("api/API_M13_Seller_Dashboard.md",
                List.of(SellerDashboardController.class, AnalyticsController.class));
        // Sprint 276：M16 進銷存管理，文件先前完全不存在（DEF-286 範圍）。採購單審批 3 個端點實作在
        // AdminController（混雜 M14/M17 端點），無法整個類別納入守門，故僅登記 ErpController；
        // 採購單審批端點暫無機械防漂移，見文件 §7 已知限制
        GUARDED.put("api/API_M16_ERP.md", List.of(ErpController.class));
        // Sprint 277：M15 內容管理系統，文件先前完全不存在（DEF-286 範圍）。四個獨立 Controller
        // 全數登記（CmsController 頁面/橫幅、PostController 貼文/分類/部落格媒體/嵌入卡片、
        // MediaController／MediaCategoryController 獨立媒體庫），見文件 §1.2 架構說明
        GUARDED.put("api/API_M15_CMS.md", List.of(
                CmsController.class, PostController.class, MediaController.class, MediaCategoryController.class));
    }

    /** 反引號包住的 `METHOD /path`：路徑到反引號、空白或 {@code ?} 為止。 */
    private static final String ROUTE = "`(GET|POST|PUT|PATCH|DELETE) (?:/api)?(/v2/[^`\\s?]*)";

    /** 端點章節標題：章節編號（{@code 4.1}、{@code 4.2b}…）後面緊接路由（{@code ### 4.1 `GET /v2/cart` — …}）。 */
    private static final Pattern HEADING = Pattern.compile("^#{2,6} [\\w.]+ " + ROUTE);

    /** {@code - **端點**: `POST /api/v2/auth/register`}。 */
    private static final Pattern BULLET = Pattern.compile("^- \\*\\*端點\\*\\*: " + ROUTE);

    @Test
    @DisplayName("Controller 的每一條路由都有記載，文件宣告的每一條路由都真的存在")
    void documentedRoutesMatchControllers() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, List<Class<?>>> entry : GUARDED.entrySet()) {
            Path doc = DOCS.resolve(entry.getKey());
            assertThat(doc).as("找不到文件 %s", doc).exists();
            String text = Files.readString(doc);

            Set<String> implemented = new TreeSet<>();
            entry.getValue().forEach(controller -> implemented.addAll(routesOf(controller)));
            Set<String> declared = declaredRoutes(text);

            for (String route : implemented) {
                if (!declared.contains(route)) {
                    problems.add(entry.getKey() + " 缺少端點：" + route + "（Controller 有、文件沒有宣告行）");
                }
            }
            for (String route : declared) {
                if (!implemented.contains(route)) {
                    problems.add(entry.getKey() + " 宣告了不存在的端點：" + route + "（文件有、Controller 沒有）");
                }
            }
        }
        assertThat(problems)
                .as("API 規格與 Controller 的路由不一致，請同步更新文件：\n%s", String.join("\n", problems))
                .isEmpty();
    }

    @Test
    @DisplayName("守門本身有東西可守：每份文件都至少涵蓋一條路由")
    void guardCoversSomething() {
        GUARDED.forEach((doc, controllers) ->
                assertThat(controllers.stream().mapToInt(c -> routesOf(c).size()).sum())
                        .as("%s 對應的 Controller 沒有任何路由，登記的類別可能不對", doc)
                        .isPositive());
    }

    /** Controller 的所有「方法 路徑」（類別層的 {@code @RequestMapping} 前綴加方法層的路徑）。 */
    static Set<String> routesOf(final Class<?> controller) {
        RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        String[] prefixes = base == null || base.path().length == 0 ? new String[] {""} : base.path();
        Set<String> routes = new TreeSet<>();
        for (Method method : controller.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null) {
                continue;
            }
            String[] paths = mapping.path().length == 0 ? new String[] {""} : mapping.path();
            for (RequestMethod httpMethod : mapping.method()) {
                for (String prefix : prefixes) {
                    for (String path : paths) {
                        routes.add(httpMethod.name() + " " + normalize(prefix + path));
                    }
                }
            }
        }
        return routes;
    }

    /** 文件的宣告行（端點章節標題與 {@code - **端點**:}）宣告的路由。 */
    private static Set<String> declaredRoutes(final String text) {
        Set<String> declared = new TreeSet<>();
        for (String line : text.split("\n")) {
            for (Pattern pattern : List.of(HEADING, BULLET)) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    declared.add(matcher.group(1) + " " + normalize(matcher.group(2)));
                }
            }
        }
        return declared;
    }

    private static String normalize(final String path) {
        String normalized = path.startsWith("/") ? path : "/" + path;
        return normalized.length() > 1 && normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }
}
