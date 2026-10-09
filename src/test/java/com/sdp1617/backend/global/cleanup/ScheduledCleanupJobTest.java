package com.sdp1617.backend.global.cleanup;

import com.sdp1617.backend.global.config.properties.CleanupProperties;
import com.sdp1617.backend.letter.service.UnsentLetterCleaner;
import com.sdp1617.backend.notification.repository.NotificationRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledCleanupJobTest {

    private final UnsentLetterCleaner unsentLetterCleaner = mock(UnsentLetterCleaner.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final UnusedImageCleaner unusedImageCleaner = mock(UnusedImageCleaner.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final ScheduledCleanupJob job = new ScheduledCleanupJob(
            new CleanupProperties(true, Duration.ofDays(1), Duration.ofDays(30), Duration.ofDays(90)),
            unsentLetterCleaner, notificationRepository, unusedImageCleaner, transactionTemplate);

    @Test
    @SuppressWarnings("unchecked")
    void 정책대로_기준_시각을_넘기고_한_단계가_실패해도_다음_단계는_진행한다() {
        when(unsentLetterCleaner.deleteNotUpdatedSince(any())).thenThrow(new IllegalStateException("DB 오류"));
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Integer>) invocation.getArgument(0)).doInTransaction(null));
        LocalDateTime before = LocalDateTime.now();
        Instant beforeInstant = Instant.now();

        job.run();

        verify(notificationRepository).deleteCreatedBefore(org.mockito.ArgumentMatchers.argThat(cutoff ->
                !cutoff.isBefore(before.minusDays(30)) && !cutoff.isAfter(LocalDateTime.now().minusDays(30))));
        verify(unusedImageCleaner).deleteUnusedImagesUploadedBefore(org.mockito.ArgumentMatchers.argThat(cutoff ->
                !cutoff.isBefore(beforeInstant.minus(Duration.ofDays(1)))
                        && !cutoff.isAfter(Instant.now().minus(Duration.ofDays(1)))));
        verify(unsentLetterCleaner).deleteNotUpdatedSince(org.mockito.ArgumentMatchers.argThat(cutoff ->
                !cutoff.isBefore(before.minusDays(90)) && !cutoff.isAfter(LocalDateTime.now().minusDays(90))));
    }
}
