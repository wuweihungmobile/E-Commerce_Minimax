package com.nextkey.ecommerce.domain.model.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.security.access.expression.ExpressionUtils;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.util.SimpleMethodInvocation;

import com.nextkey.ecommerce.api.controller.OrderController;
import com.nextkey.ecommerce.api.controller.OrderPaymentController;
import com.nextkey.ecommerce.api.controller.PaymentController;

/**
 * 一般買家在<b>生產權限</b>下能不能走完「下單 → 付款 → 取消」，以及不能自己退款／改訂單狀態（Sprint 216，DEF-298）。
 *
 * <p><b>為什麼要有這支測試</b>：付款端點原本只接受 {@code order:update}，而生產的 BUYER 從來沒有這個權限，
 * 一般買家建立訂單後付款一律 403。整合測試抓不到，因為 {@code IntegrationTestConfiguration} 用一份手抄的
 * 權限清單取代真實 {@link RolePermissionMapping}，那份清單多給了 BUYER {@code order:update}；Playwright 則從未
 * 走到付款那一步。所以這裡直接用真實的權限表，對控制器方法上真實的 {@code @PreAuthorize} 求值。
 *
 * <p>「不可呼叫」那一組同樣重要：這些端點在服務層都放行「訂單本人」，若買家拿得到權限，就能自己退款、
 * 或把自己的訂單直接改成已出貨／已完成。所以付款端點不能用「給 BUYER {@code order:update}」來修。
 */
@DisplayName("買家訂單端點授權（生產權限表，DEF-298）")
class BuyerOrderEndpointAuthorizationTest {

    private static final DefaultMethodSecurityExpressionHandler HANDLER = new DefaultMethodSecurityExpressionHandler();

    @ParameterizedTest(name = "買家可呼叫 {0}.{1}")
    @DisplayName("買家下單、付款、取消所需的端點都放行")
    @CsvSource({
            "OrderController, createOrder",
            "OrderController, getOrders",
            "OrderController, getOrder",
            "OrderController, getOrderLogs",
            "OrderController, cancelOrder",
            "OrderPaymentController, getOrderPaymentState",
            "OrderPaymentController, mockPaySuccess",
            "OrderPaymentController, mockPayFailure",
            "OrderPaymentController, initiateStripeCheckout",
            "OrderPaymentController, confirmStripeCheckout",
    })
    void buyerCanReachPurchaseJourney(final String controller, final String methodName) {
        assertThat(buyerIsGranted(controller, methodName))
                .as("BUYER 必須能呼叫 %s.%s（前端買家頁面會呼叫）", controller, methodName)
                .isTrue();
    }

    @ParameterizedTest(name = "買家不可呼叫 {0}.{1}")
    @DisplayName("服務層放行訂單本人的退款與狀態變更端點，買家必須在授權層就被擋下")
    @CsvSource({
            "OrderPaymentController, mockRefund",
            "OrderController, updateStatus",
            "PaymentController, processRefund",
    })
    void buyerCannotRefundOrChangeStatusOfOwnOrder(final String controller, final String methodName) {
        assertThat(buyerIsGranted(controller, methodName))
                .as("BUYER 不可呼叫 %s.%s（服務層放行訂單本人，等於買家可自己退款或改狀態）", controller, methodName)
                .isFalse();
    }

    private static boolean buyerIsGranted(final String controller, final String methodName) {
        Class<?> type = controllerClass(controller);
        Method method = findMethod(type, methodName);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuthorize).as("%s.%s 應有 @PreAuthorize", controller, methodName).isNotNull();

        List<SimpleGrantedAuthority> authorities = new RolePermissionMapping()
                .getAuthorities(User.UserRole.BUYER).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        Authentication buyer = new UsernamePasswordAuthenticationToken("buyer", "n/a", authorities);

        EvaluationContext context = HANDLER.createEvaluationContext(buyer, new SimpleMethodInvocation(Mockito.mock(type), method));
        Expression expression = HANDLER.getExpressionParser().parseExpression(preAuthorize.value());
        return ExpressionUtils.evaluateAsBoolean(expression, context);
    }

    private static Class<?> controllerClass(final String simpleName) {
        return switch (simpleName) {
            case "OrderController" -> OrderController.class;
            case "OrderPaymentController" -> OrderPaymentController.class;
            case "PaymentController" -> PaymentController.class;
            default -> throw new IllegalArgumentException("未登記的控制器：" + simpleName);
        };
    }

    /** 以方法名稱找端點；改名時測試會直接失敗，而不是默默略過。 */
    private static Method findMethod(final Class<?> type, final String name) {
        List<Method> matches = Arrays.stream(type.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .toList();
        assertThat(matches).as("%s 應恰有一個名為 %s 的方法", type.getSimpleName(), name).hasSize(1);
        return matches.get(0);
    }
}
