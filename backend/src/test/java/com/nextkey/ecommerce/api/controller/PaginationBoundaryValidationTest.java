package com.nextkey.ecommerce.api.controller;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.nextkey.ecommerce.integration.IntegrationTestConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 165（DEF-216）：延伸 Sprint 164 §7 誠實揭露——{@code page}/{@code size} 查詢參數若
 * 帶入 {@code size<=0} 或 {@code page<0}，Spring Data 的 {@code PageRequest} 建構子本身
 * （{@code AbstractPageRequest}）會直接拋出未攔截的 {@code IllegalArgumentException}，落入
 * 全域例外處理器的 catch-all 變成 500。此問題與 Sprint 164 的「上限」修復
 * （{@code Math.min(size, 100)}）不同性質、不受其保護——{@code Math.min(0, 100)} 仍是 0。
 *
 * <p>修法採「共用工具方法」（{@code PageableUtils}）靜默正規化 page/size 到合法範圍
 * （{@code page<0 → 0}、{@code size<=0 → 1}），而非攔截例外轉 400——與 Sprint 162/163 的
 * 「全域例外處理器轉 400」不同決策：此處的 {@code IllegalArgumentException} 並非框架層級、
 * 只源自 client 輸入錯誤的例外（與 {@code HttpMessageNotReadableException}/
 * {@code MethodArgumentTypeMismatchException} 不同性質），Sprint 161 §4 已判斷全域攔截
 * {@code IllegalArgumentException} 有掩蓋真正程式錯誤的風險，故改在源頭正規化參數，
 * 讓請求正常成功（200），而非用 400 回絕。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("Sprint 165: 分頁 page/size 邊界值正規化")
class PaginationBoundaryValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /v2/posts?size=0 → 200（靜默正規化為合法頁面大小），而非 500")
    void getPublishedPosts_sizeZero_returns200NotInternalServerError() throws Exception {
        mockMvc.perform(get("/v2/posts")
                        .param("tenantId", UUID.randomUUID().toString())
                        .param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /v2/posts?page=-1 → 200（靜默正規化為合法頁碼），而非 500")
    void getPublishedPosts_negativePage_returns200NotInternalServerError() throws Exception {
        mockMvc.perform(get("/v2/posts")
                        .param("tenantId", UUID.randomUUID().toString())
                        .param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
