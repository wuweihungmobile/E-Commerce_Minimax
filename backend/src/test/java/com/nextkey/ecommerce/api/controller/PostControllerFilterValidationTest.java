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
 * Sprint 161 回歸測試：{@link PostController#getPosts}/{@link PostController#getMediaList}
 * 的 {@code status}/{@code fileType} query param 若帶入非法字串，先前會因未攔截的
 * {@code IllegalArgumentException}（{@code Post.PostStatus.valueOf}/{@code MediaAsset.FileType.valueOf}）
 * 被全域例外處理器的 catch-all 轉成 500，而非正確的 400。
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

    @Test
    @DisplayName("GET /v2/dashboard/media?fileType=無效值 回傳 400（E-9000），而非 500")
    void getMediaList_invalidFileType_returns400NotInternalServerError() throws Exception {
        mockMvc.perform(get("/v2/dashboard/media").param("fileType", "NOT_A_REAL_FILE_TYPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("E-9000"));
    }
}
