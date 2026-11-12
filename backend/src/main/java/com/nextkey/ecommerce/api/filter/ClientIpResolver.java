package com.nextkey.ecommerce.api.filter;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * 「這個請求是從哪個 IP 來的」的唯一決定點（Sprint 341，DEF-501／DEF-487）。
 * {@link RateLimitFilter}（匿名每 IP）與 {@link LoginRateLimitFilter}（登入／註冊每 IP）都只透過它取得來源 IP。
 *
 * <p><b>全專案只有一個參數</b>：{@code app.network.trusted-proxy-hops}（環境變數 {@code APP_TRUSTED_PROXY_HOPS}），
 * 意思是「訪客的請求在抵達本後端之前，要經過幾層反向代理／負載平衡器／CDN」：
 * <ul>
 *   <li>{@code 0}（預設）：前面沒有任何代理。來源 IP ＝ TCP 連線的對端位址（{@link HttpServletRequest#getRemoteAddr()}），
 *       完全不看 {@code X-Forwarded-For}——與本類別出現之前的行為一模一樣。</li>
 *   <li>{@code N ≥ 1}：前面有 N 層代理，每一層都會把「它看到的對端位址」附加在 {@code X-Forwarded-For} 最右邊，
 *       所以<b>從右邊數第 N 個</b>才是真正的訪客。左邊更多的項目是訪客自己（或更上游）填的，可以偽造，一律不採信。</li>
 * </ul>
 *
 * <p>為什麼用「層數」而不是「代理的 IP 清單」：雲端負載平衡器與 CDN 的位址會變，也很難查；層數只有操作者一定知道的事實。
 * 代價是這個數字必須對「每一條」通往後端的路徑都成立——後端不能同時有繞過代理的直連入口，
 * 否則直連者可以自己填 {@code X-Forwarded-For} 假冒任何 IP 來繞過限流（所以預設是 0，不是 1）。
 *
 * <p>前端 Next 伺服器代訪客呼叫後端時，會把收到的 {@code X-Forwarded-For} 原封不動帶過來（不附加、不改寫），
 * 因此「訪客 → 代理 → Next → 後端」與「訪客 → 代理 → 後端」到後端時是同樣的 N 個項目，同一個參數兩條路都適用。
 *
 * <p>資料不足或不合法時一律退回對端位址（安全的一邊）：項目比層數少（請求繞過了代理、或層數設太大）、
 * 選到的項目不是合法的 IP 位址字面值（{@code unknown}、{@code _hidden}、任意文字）。
 */
@Slf4j
final class ClientIpResolver {

    /** 層數上限：超過就幾乎一定是設錯（例如把埠號或 IP 位址填進來了），啟動時直接失敗而不是默默接受。 */
    static final int MAX_TRUSTED_PROXY_HOPS = 10;

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    /** IPv6 位址文字最長 45 個字元（含 IPv4 對應格式 ::ffff:255.255.255.255）。 */
    private static final int MAX_ADDRESS_LENGTH = 45;
    private static final int IPV4_OCTETS = 4;
    private static final int MAX_OCTET = 255;
    private static final AtomicBoolean STARTUP_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean MISMATCH_WARNED = new AtomicBoolean();

    private final int trustedProxyHops;

    ClientIpResolver(final int trustedProxyHops) {
        if (trustedProxyHops < 0 || trustedProxyHops > MAX_TRUSTED_PROXY_HOPS) {
            throw new IllegalArgumentException("app.network.trusted-proxy-hops（APP_TRUSTED_PROXY_HOPS）必須是 0～"
                    + MAX_TRUSTED_PROXY_HOPS + " 的整數（訪客到後端之間的反向代理層數；沒有代理填 0），目前是 " + trustedProxyHops);
        }
        this.trustedProxyHops = trustedProxyHops;
        if (STARTUP_LOGGED.compareAndSet(false, true)) {
            log.info("[ClientIp] trusted-proxy-hops={}（{}）", trustedProxyHops, trustedProxyHops == 0
                    ? "沒有代理：來源 IP 取 TCP 對端位址，不採信 X-Forwarded-For"
                    : "從 X-Forwarded-For 由右數第 " + trustedProxyHops + " 項取訪客 IP；後端不可有繞過代理的直連入口");
        }
    }

    /** 這個請求的來源 IP（已正規化；對端位址原樣回傳）。 */
    String resolve(final HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (trustedProxyHops == 0) {
            return peer;
        }
        List<String> chain = forwardedChain(request);
        if (chain.size() < trustedProxyHops) {
            warnOnce("X-Forwarded-For 只有 " + chain.size() + " 項，少於設定的代理層數 " + trustedProxyHops
                    + "：請求不是經過代理來的（或層數設太大），改用對端位址 " + peer);
            return peer;
        }
        String client = normalize(chain.get(chain.size() - trustedProxyHops));
        if (client == null) {
            warnOnce("X-Forwarded-For 由右數第 " + trustedProxyHops + " 項不是合法的 IP 位址，改用對端位址 " + peer);
            return peer;
        }
        return client;
    }

    /** 所有 X-Forwarded-For 標頭行依序攤平成一個清單（同一個標頭可能被拆成多行送來）；空項目略過。 */
    private static List<String> forwardedChain(final HttpServletRequest request) {
        List<String> chain = new ArrayList<>();
        Enumeration<String> headerValues = request.getHeaders(FORWARDED_FOR);
        if (headerValues == null) {
            return chain; // Servlet 規格允許容器不開放標頭存取而回 null
        }
        for (String headerValue : Collections.list(headerValues)) {
            for (String part : headerValue.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    chain.add(trimmed);
                }
            }
        }
        return chain;
    }

    private static void warnOnce(final String message) {
        if (MISMATCH_WARNED.compareAndSet(false, true)) {
            log.warn("[ClientIp] {}（此警告只印一次；若所有訪客共用同一個限流桶，請檢查 APP_TRUSTED_PROXY_HOPS）", message);
        }
    }

    /**
     * 把 X-Forwarded-For 的一項轉成正規化的 IP 位址字串；不是合法的位址字面值回 null。
     * 接受 {@code 1.2.3.4}、{@code 1.2.3.4:5678}、{@code 2001:db8::1}、{@code [2001:db8::1]}、{@code [2001:db8::1]:443}。
     * 刻意不用 {@code InetAddress.getByName} 處理 IPv4：它接受 {@code 1.2}、{@code 0x7f.1} 這類簡寫，
     * 而且對非位址字串會去做 DNS 查詢；IPv4 自己嚴格解析，IPv6 先確認只含十六進位字元與冒號才交給它（不會碰 DNS）。
     */
    static String normalize(final String raw) {
        String text = raw.trim();
        if (text.startsWith("[")) {
            int end = text.indexOf(']');
            if (end < 0) {
                return null;
            }
            text = text.substring(1, end);
        } else if (text.indexOf(':') >= 0 && text.indexOf(':') == text.lastIndexOf(':') && text.indexOf('.') >= 0) {
            text = text.substring(0, text.indexOf(':')); // 1.2.3.4:5678（只有一個冒號且有句點＝IPv4 加埠號）
        }
        if (text.isEmpty() || text.length() > MAX_ADDRESS_LENGTH) {
            return null;
        }
        if (text.indexOf(':') < 0) {
            return normalizeIpv4(text);
        }
        return normalizeIpv6(text);
    }

    private static String normalizeIpv4(final String text) {
        String[] octets = text.split("\\.", -1);
        if (octets.length != IPV4_OCTETS) {
            return null;
        }
        StringBuilder normalized = new StringBuilder();
        for (String octet : octets) {
            // 只收 1～3 位數字、不含前導 0（避免「010 是八進位還是十進位」的歧義）
            boolean wellFormed = !octet.isEmpty() && octet.length() <= 3 && octet.chars().allMatch(c -> c >= '0' && c <= '9');
            boolean leadingZero = octet.length() > 1 && octet.charAt(0) == '0';
            if (!wellFormed || leadingZero || Integer.parseInt(octet) > MAX_OCTET) {
                return null;
            }
            if (normalized.length() > 0) {
                normalized.append('.');
            }
            normalized.append(octet);
        }
        return normalized.toString();
    }

    private static String normalizeIpv6(final String text) {
        if (!text.chars().allMatch(c -> c == ':' || c == '.' || Character.digit(c, 16) >= 0)) {
            return null; // 含 %（區域識別）、空白或其他字元：不是我們要接受的字面值
        }
        try {
            // 已確認含冒號且只有十六進位字元：JDK 把它當 IPv6 字面值解析，格式錯誤直接丟例外，不會做 DNS 查詢。
            // IPv4 對應位址（::ffff:1.2.3.4）會得到 IPv4 位址，與直接送 IPv4 的同一訪客得到同一個限流桶。
            return InetAddress.getByName(text).getHostAddress();
        } catch (UnknownHostException ex) {
            return null;
        }
    }
}
