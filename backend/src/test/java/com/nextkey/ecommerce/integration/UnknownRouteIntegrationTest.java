package com.nextkey.ecommerce.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nextkey.ecommerce.api.dto.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEF-279（Sprint 199）：呼叫端打錯路徑／方法／內容型別，是「呼叫端的錯」，不是「伺服器出錯」。
 *
 * <p>Spring 6.1 起，找不到路由會拋 {@code NoResourceFoundException}（不再是空的 404）。
 * {@code GlobalExceptionHandler} 沒有專屬處理器時，它落入 {@code Exception} 的 catch-all，
 * 造成兩個實際後果：回 500（前端／掃描器把它當成後端故障），以及每次請求都寫一筆 ERROR 加完整堆疊
 * （打錯路徑的用戶端或掃描器會洗版日誌、觸發 5xx 告警）。所以「狀態碼」與「日誌不是 ERROR」兩者都要守住。
 *
 * <p>走完整過濾鏈驗證：路由不存在的例外是在 Security 通過之後、DispatcherServlet 內才拋出，
 * 用單元測試直接呼叫處理器抓不到「Spring 到底拋了哪個例外」。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@DisplayName("IT-UNKNOWN-ROUTE: 呼叫端錯誤不可被當成伺服器錯誤")
class UnknownRouteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private Logger handlerLogger;
    private ListAppender<ILoggingEvent> logEvents;

    @BeforeEach
    void captureHandlerLog() {
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logEvents = new ListAppender<>();
        logEvents.start();
        handlerLogger.addAppender(logEvents);
    }

    @AfterEach
    void releaseHandlerLog() {
        handlerLogger.detachAppender(logEvents);
    }

    @Test
    @WithErpSecurity(role = "BUYER")
    @DisplayName("IT-UNKNOWN-01: 已登入者請求不存在的路徑 → 404 + E-4041，而非 500 + E-9900")
    void unknownPath_authenticated_is404() throws Exception {
        mockMvc.perform(get("/v2/no-such-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("E-4041"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @WithErpSecurity(role = "BUYER")
    @DisplayName("IT-UNKNOWN-02: 找不到路徑不寫 ERROR 日誌（否則掃描器一掃就洗版日誌、觸發 5xx 告警）")
    void unknownPath_doesNotLogAtError() throws Exception {
        mockMvc.perform(get("/v2/no-such-endpoint")).andExpect(status().isNotFound());

        assertThat(logEvents.list)
                .as("GlobalExceptionHandler 對呼叫端錯誤不應記錄 ERROR")
                .noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.ERROR));
    }

    @Test
    @WithErpSecurity(role = "BUYER")
    @DisplayName("IT-UNKNOWN-03: 路徑存在但方法不對 → 405 + Allow 標頭（RFC 9110 要求），而非 500")
    void wrongMethod_is405_withAllowHeader() throws Exception {
        mockMvc.perform(post("/v2/auth/me"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("E-9000"));
    }

    @Test
    @DisplayName("IT-UNKNOWN-04: 內容型別不支援 → 415，而非 500")
    void unsupportedMediaType_is415() throws Exception {
        mockMvc.perform(post("/v2/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("E-9000"));
    }
}
