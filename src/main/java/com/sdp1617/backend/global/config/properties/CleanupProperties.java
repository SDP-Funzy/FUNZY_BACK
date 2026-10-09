package com.sdp1617.backend.global.config.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 매일 새벽 정리 작업 설정 (#122).
 *
 * @param enabled               실제 운영 서버 .env의 CLEANUP_ENABLED=true로만 켠다 (기본 꺼짐). 로컬도 기본값으로
 *                              운영과 같은 S3 버킷을 쓰므로, 로컬 DB 기준으로 정리하면 운영 사진을 지울 수 있다.
 * @param imageGracePeriod      올린 지 이 시간이 안 된 사진은 지우지 않는다 (사진을 올리고 카드를 저장하기 전 사이)
 * @param notificationRetention 만든 지 이 기간이 지난 알림을 지운다
 * @param unsentLetterRetention 마지막 수정 후 이 기간이 지난 보내지 않은 편지를 지운다
 */
@ConfigurationProperties(prefix = "app.cleanup")
public record CleanupProperties(
        boolean enabled,
        Duration imageGracePeriod,
        Duration notificationRetention,
        Duration unsentLetterRetention
) {
}
