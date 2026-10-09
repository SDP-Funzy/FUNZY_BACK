package com.sdp1617.backend.global.cleanup;

import com.sdp1617.backend.global.config.properties.CleanupProperties;
import com.sdp1617.backend.letter.service.UnsentLetterCleaner;
import com.sdp1617.backend.notification.repository.NotificationRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 서버 비용을 줄이기 위해 매일 새벽 오래된 데이터와 안 쓰는 사진을 지운다 (#122).
 * 순서: 방치된 편지 → 오래된 알림 → 안 쓰는 사진 (편지를 지우면 그 카드 사진이 안 쓰는 사진이 되어 같은 날 함께 지워진다).
 * 한 단계가 실패해도 다음 단계는 진행한다. 서버가 여러 대가 되면 ShedLock 등으로 한 대에서만 돌게 해야 한다 (지금은 1대).
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.cleanup", name = "enabled", havingValue = "true")
public class ScheduledCleanupJob {

    private final CleanupProperties properties;
    private final UnsentLetterCleaner unsentLetterCleaner;
    private final NotificationRepository notificationRepository;
    private final UnusedImageCleaner unusedImageCleaner;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDateTime now = LocalDateTime.now();
        runStep("보내지 않은 편지", () -> unsentLetterCleaner.deleteNotUpdatedSince(
                now.minus(properties.unsentLetterRetention())));
        runStep("알림", () -> transactionTemplate.execute(status -> notificationRepository.deleteCreatedBefore(
                now.minus(properties.notificationRetention()))));
        runStep("안 쓰는 사진", () -> unusedImageCleaner.deleteUnusedImagesUploadedBefore(
                Instant.now().minus(properties.imageGracePeriod())));
    }

    private void runStep(String name, Supplier<Integer> step) {
        try {
            log.info("정리 작업 완료: {} {}건 삭제", name, step.get());
        } catch (RuntimeException e) {
            log.error("정리 작업 실패: {}", name, e);
        }
    }
}
