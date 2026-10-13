package com.sdp1617.backend.global.config.properties;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 브라우저에서 API를 호출할 수 있는 프론트 주소 (#154).
 *
 * @param allowedOrigins CORS_ALLOWED_ORIGINS(쉼표 구분)로 정한다. 쿠키·인증 정보를 함께 보내는 설정이라 "*"는 쓸 수 없고,
 *                       "https://funzy.kr"처럼 scheme·host(·port)만 적는다. 끝의 "/"와 기본 포트(:443, :80)는 떼고,
 *                       경로가 붙어 있으면 기동하지 않는다
 *                       (브라우저가 보내는 Origin에는 경로가 없어 아무 요청도 허용되지 않기 때문)
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .map(CorsProperties::normalize)
                .distinct()
                .toList();
        if (allowedOrigins.isEmpty()) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS에 프론트 주소를 1개 이상 설정해야 합니다.");
        }
    }

    private static String normalize(String origin) {
        String value = origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw invalid(origin);
        }
        boolean httpScheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        boolean originOnly = uri.getHost() != null && uri.getUserInfo() == null
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                && uri.getRawQuery() == null && uri.getRawFragment() == null;
        // URI는 빈 포트("https://funzy.kr:")와 범위 밖 포트도 받아 주므로 따로 막는다
        boolean validPort = uri.getRawAuthority() != null && !uri.getRawAuthority().endsWith(":")
                && (uri.getPort() == -1 || (uri.getPort() >= 1 && uri.getPort() <= 65535));
        if (!httpScheme || !originOnly || !validPort) {
            throw invalid(origin);
        }
        // 브라우저는 기본 포트(https 443, http 80)를 Origin에 적지 않으므로, 남겨 두면 문자열이 달라 모두 거절된다
        boolean defaultPort = uri.getPort() == ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        return defaultPort ? uri.getScheme() + "://" + uri.getHost() : value;
    }

    private static IllegalStateException invalid(String origin) {
        return new IllegalStateException(
                "CORS_ALLOWED_ORIGINS의 \"" + origin + "\"는 올바른 주소가 아닙니다. https://funzy.kr 처럼 scheme·host(·port)만 적어주세요.");
    }
}
