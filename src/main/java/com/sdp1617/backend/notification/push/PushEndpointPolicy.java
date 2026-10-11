package com.sdp1617.backend.notification.push;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/**
 * 구독 주소로 받을 수 있는 곳. 서버가 이 주소로 직접 요청을 보내므로, 아무 주소나 받으면
 * 서버 내부망 등 엉뚱한 곳으로 요청을 보내게 할 수 있다. 알려진 브라우저 푸시 서비스의 https 주소만 받는다.
 */
public final class PushEndpointPolicy {

    private PushEndpointPolicy() {
    }

    public static boolean isAllowed(String endpoint, List<String> allowedHosts) {
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return allowedHosts.stream()
                .map(allowed -> allowed.toLowerCase(Locale.ROOT))
                .anyMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed));
    }
}
