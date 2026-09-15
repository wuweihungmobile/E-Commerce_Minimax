package com.nextkey.ecommerce.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.nextkey.ecommerce.integration.IntegrationTestConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 163 回歸測試：延伸 Sprint 161 §7 誠實揭露「未對 {@code @RequestParam UUID}/
 * {@code @RequestParam LocalDate} 等 Spring 內建型別轉換失敗情境做系統性掃描」。
 * {@link OrderController#getOrder} 的 {@code @PathVariable UUID orderId} 帶入非
 * UUID 字串時，Spring 在進入 Controller 方法前的參數綁定階段就會拋出
 * {@code MethodArgumentTypeMismatchException}，先前全域例外處理器未攔截此類型，
 * 落入 catch-all 變成 500，而非正確的 400。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@WithMockUser(username = "buyer", authorities = "order:read")
@DisplayName("Sprint 163: @PathVariable/@RequestParam 型別轉換失敗防護")
class RequestParamTypeMismatchValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /v2/orders/{orderId} 帶非 UUID 字串 → 400（E-9000），而非 500")
    void getOrder_nonUuidPathVariable_returns400NotInternalServerError() throws Exception {
        mockMvc.perform(get("/v2/orders/not-a-real-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("E-9000"));
    }
}
