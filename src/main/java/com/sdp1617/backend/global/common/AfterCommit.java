package com.sdp1617.backend.global.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * DB 트랜잭션이 실제로 커밋된 뒤에만 외부 상태(S3, Redis 등)를 바꾸기 위한 헬퍼.
 * 커밋 전에 바꿔버리면 이후 커밋이 실패했을 때 DB는 롤백되는데 외부 상태만 바뀐 채로 남는다.
 */
@Slf4j
public final class AfterCommit {

    private AfterCommit() {
    }

    /** 트랜잭션이 없는 컨텍스트(예: 단위 테스트)에서는 즉시 실행한다. */
    public static void run(Runnable action) {
        run(action, action);
    }

    /**
     * 실패해도 예외를 던지지 않고 로그만 남기는 커밋 후 정리 작업용.
     * 커밋 후 콜백 하나가 예외를 던지면 Spring은 그 뒤에 등록된 콜백(예: 세션 폐기 이벤트 리스너)을 실행하지 않고,
     * DB는 이미 커밋됐는데 요청은 500으로 실패한다. 부가적인 정리(Redis 키 삭제 등)가 핵심 후처리를 막지 않게 한다.
     */
    public static void runBestEffort(String description, Runnable action) {
        run(() -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                log.warn("커밋 후 정리 작업 실패: {}", description, exception);
            }
        });
    }

    /**
     * @param afterCommit        커밋 후 실행할 동작
     * @param withoutTransaction 트랜잭션이 없을 때 대신 즉시 실행할 동작
     */
    public static void run(Runnable afterCommit, Runnable withoutTransaction) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            withoutTransaction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                afterCommit.run();
            }
        });
    }
}
