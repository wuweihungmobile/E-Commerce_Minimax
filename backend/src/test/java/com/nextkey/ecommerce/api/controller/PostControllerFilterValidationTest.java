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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sprint 161 回歸測試：{@link PostController#getPosts} 的 {@code status} query param 若帶入非法字串，
 * 先前會因未攔截的 {@code IllegalArgumentException}（{@code Post.PostStatus.valueOf}）
 * 被全域例外處理器的 catch-all 轉成 500，而非正確的 400。
 *
 * <p>（原本同時涵蓋 {@code getMediaList} 的 {@code fileType}；該端點已於 Sprint 334 隨 DEF-474 移除，
 * 對應案例一併移除。M18 的 {@code GET /v2/media} 另有自己的參數驗證。）
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@WithMockUser(username = "owner", roles = "STORE_OWNER")
@DisplayName("Sprint 161: PostController 列表 query param 非法列舉值防護")
class PostControllerFilterValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /v2/dashboard/posts?status=無效值 回傳 400（E-9000），而非 500")
    void getPosts_invalidStatus_returns400NotInternalServerError() throws Exception {
        mockMvc.perform(get("/v2/dashboard/posts").param("status", "NOT_A_REAL_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("E-9000"));
    }
}
