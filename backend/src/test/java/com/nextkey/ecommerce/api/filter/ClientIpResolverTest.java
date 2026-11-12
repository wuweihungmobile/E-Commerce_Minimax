package com.nextkey.ecommerce.api.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * ClientIpResolver 單元測試（Sprint 341，DEF-501／DEF-487）。
 *
 * <p>要守住的意圖有兩個，且彼此拉扯：
 * <ol>
 *   <li><b>前面有代理時，要找得到真正的訪客</b>——否則所有訪客的對端位址都是代理，共用同一個限流桶，正常使用者互相擋（DEF-501）。</li>
 *   <li><b>不能因此讓訪客自己決定自己的 IP</b>——{@code X-Forwarded-For} 左邊的項目是訪客填的；預設（沒設參數）完全不看這個標頭，
 *       設了參數也只取代理自己附加的那一項（由右數第 N 個）。測試用「偽造的左側項目」與「沒有代理的直連」兩種攻擊情境驗證。</li>
 * </ol>
 */
@DisplayName("ClientIpResolver 單元測試（Sprint 341，DEF-501）")
class ClientIpResolverTest {

    private static final String PROXY = "10.0.0.9";
    private static final String VISITOR = "203.0.113.7";

    private static MockHttpServletRequest requestFrom(final String peer, final String... forwardedForLines) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v2/products");
        request.setRemoteAddr(peer);
        for (String line : forwardedForLines) {
            request.addHeader("X-Forwarded-For", line);
        }
        return request;
    }

    @Test
    @DisplayName("預設（層數 0）：永遠用對端位址，X-Forwarded-For 填什麼都不採信（直連的偽造攻擊無效）")
    void zeroHops_ignoresForwardedForCompletely() {
        ClientIpResolver resolver = new ClientIpResolver(0);

        assertThat(resolver.resolve(requestFrom(VISITOR))).isEqualTo(VISITOR);
        assertThat(resolver.resolve(requestFrom(VISITOR, "1.2.3.4"))).as("訪客自己填的標頭不能決定他的 IP").isEqualTo(VISITOR);
        assertThat(resolver.resolve(requestFrom(PROXY, VISITOR))).as("沒設層數＝不知道有代理，照舊看對端").isEqualTo(PROXY);
    }

    @Test
    @DisplayName("一層代理：取 X-Forwarded-For 最右邊一項（代理附加的訪客位址）")
    void oneHop_takesRightmostEntry() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        assertThat(resolver.resolve(requestFrom(PROXY, VISITOR))).isEqualTo(VISITOR);
    }

    @Test
    @DisplayName("一層代理：訪客在左邊偽造的項目不被採信，只認代理附加的最右邊一項")
    void oneHop_forgedLeftEntriesAreIgnored() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        assertThat(resolver.resolve(requestFrom(PROXY, "1.2.3.4, 5.6.7.8, " + VISITOR))).isEqualTo(VISITOR);
    }

    @Test
    @DisplayName("兩層代理（例如 CDN＋負載平衡器）：取由右數第二項；再左邊的偽造項目一樣不採信")
    void twoHops_takesSecondFromRight() {
        ClientIpResolver resolver = new ClientIpResolver(2);

        assertThat(resolver.resolve(requestFrom(PROXY, VISITOR + ", 198.51.100.1"))).isEqualTo(VISITOR);
        assertThat(resolver.resolve(requestFrom(PROXY, "9.9.9.9, " + VISITOR + ", 198.51.100.1"))).isEqualTo(VISITOR);
    }

    @Test
    @DisplayName("設了層數卻沒有經過代理（直連、沒有標頭）：退回對端位址，不會取到空值或亂值")
    void noForwardedFor_fallsBackToPeer() {
        assertThat(new ClientIpResolver(1).resolve(requestFrom(VISITOR))).isEqualTo(VISITOR);
    }

    @Test
    @DisplayName("標頭項目比層數少（層數設太大，或請求繞過了其中一層代理）：退回對端位址，而不是信任一個不足的鏈")
    void fewerEntriesThanHops_fallsBackToPeer() {
        ClientIpResolver resolver = new ClientIpResolver(2);

        // 直連的攻擊者只送一個偽造項目：項目數 1 < 層數 2，不採信
        assertThat(resolver.resolve(requestFrom(VISITOR, "1.2.3.4"))).isEqualTo(VISITOR);
    }

    @Test
    @DisplayName("同一個標頭被拆成多行送來時，依序攤平後再數（不能只看第一行）")
    void multipleHeaderLines_areFlattenedInOrder() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        assertThat(resolver.resolve(requestFrom(PROXY, "1.2.3.4", VISITOR))).isEqualTo(VISITOR);
    }

    @Test
    @DisplayName("項目前後的空白、空項目（連續逗號）不影響計算")
    void whitespaceAndEmptyEntries_areIgnored() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        assertThat(resolver.resolve(requestFrom(PROXY, " 1.2.3.4 ,, " + VISITOR + " ,"))).isEqualTo(VISITOR);
    }

    @Test
    @DisplayName("項目帶埠號或中括號時去掉，同一訪客不會因為寫法不同而分到不同的桶")
    void portsAndBrackets_areStripped() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        assertThat(resolver.resolve(requestFrom(PROXY, VISITOR + ":51234"))).isEqualTo(VISITOR);
        assertThat(resolver.resolve(requestFrom(PROXY, "[2001:db8::1]:443"))).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(resolver.resolve(requestFrom(PROXY, "[2001:db8::1]"))).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    @DisplayName("IPv6 不同寫法（縮寫、補零、大小寫）得到同一個字串；IPv4 對應位址等同 IPv4（同一訪客同一個桶）")
    void ipv6Spellings_areNormalizedToOneBucket() {
        ClientIpResolver resolver = new ClientIpResolver(1);

        String compact = resolver.resolve(requestFrom(PROXY, "2001:db8::1"));
        assertThat(resolver.resolve(requestFrom(PROXY, "2001:0DB8:0:0:0:0:0:1"))).isEqualTo(compact);
        assertThat(resolver.resolve(requestFrom(PROXY, "::ffff:" + VISITOR))).isEqualTo(VISITOR);
    }

    @ParameterizedTest(name = "不是合法的 IP 位址「{0}」→ 退回對端位址")
    @ValueSource(strings = {
        "unknown", "_hidden", "evil.example.com", "deadbeef", "1.2", "1.2.3", "0x7f.0.0.1", "127.1", "999.1.1.1",
        "01.2.3.4", "1.2.3.4.5", "1.2.3.4; DROP TABLE", "fe80::1%eth0", "::g", "1:2", "[::1", "-1.2.3.4",
        // 數字區域識別：JDK 會接受 fe80::1%1，並在 getHostAddress() 帶回 %1——同一個位址會因 %N 後綴分到不同的限流桶
        "fe80::1%1", "2001:db8::1%25"
    })
    void invalidEntries_fallBackToPeer(final String forged) {
        ClientIpResolver resolver = new ClientIpResolver(1);

        assertThat(resolver.resolve(requestFrom(PROXY, forged))).isEqualTo(PROXY);
    }

    @Test
    @DisplayName("層數超出 0～10（多半是把埠號或 IP 填進來了）：啟動時就失敗，訊息指出參數名稱與合法範圍")
    void outOfRangeHops_failFastWithActionableMessage() {
        assertThatThrownBy(() -> new ClientIpResolver(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APP_TRUSTED_PROXY_HOPS")
                .hasMessageContaining("0～10");
        assertThatThrownBy(() -> new ClientIpResolver(ClientIpResolver.MAX_TRUSTED_PROXY_HOPS + 1))
                .isInstanceOf(IllegalArgumentException.class);
        // 邊界值本身合法
        assertThat(new ClientIpResolver(0)).isNotNull();
        assertThat(new ClientIpResolver(ClientIpResolver.MAX_TRUSTED_PROXY_HOPS)).isNotNull();
    }
}
