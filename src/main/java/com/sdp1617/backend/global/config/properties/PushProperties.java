package com.sdp1617.backend.global.config.properties;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 웹 푸시 설정 (#89).
 *
 * @param enabled              운영 .env의 PUSH_ENABLED=true로 켠다. 꺼져 있으면 구독은 저장하되 발송하지 않는다
 * @param vapidPublicKey       VAPID 공개키 (압축하지 않은 P-256 점, base64url). 프론트가 구독할 때 쓴다
 * @param vapidPrivateKey      VAPID 비밀키 (32바이트, base64url)
 * @param subject              푸시 서비스가 문제 시 연락할 곳 (mailto: 또는 https URL)
 * @param ttl                  브라우저가 꺼져 있을 때 푸시 서비스가 메시지를 보관하는 기간
 * @param allowedEndpointHosts 구독 주소로 받을 브라우저 푸시 서비스 도메인. 이 도메인이거나 하위 도메인인 https 주소만 받는다
 */
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(
        boolean enabled,
        String vapidPublicKey,
        String vapidPrivateKey,
        String subject,
        Duration ttl,
        List<String> allowedEndpointHosts
) {
    public PushProperties {
        if (ttl == null) {
            ttl = Duration.ofDays(1);
        }
        allowedEndpointHosts = allowedEndpointHosts == null ? List.of() : List.copyOf(allowedEndpointHosts);
        if (enabled && (isBlank(vapidPublicKey) || isBlank(vapidPrivateKey) || isBlank(subject))) {
            throw new IllegalStateException(
                    "app.push.enabled=true이면 VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, VAPID_SUBJECT 설정이 필요합니다.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
