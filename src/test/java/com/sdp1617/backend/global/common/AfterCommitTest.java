package com.sdp1617.backend.global.common;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AfterCommitTest {

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void 트랜잭션이_없으면_즉시_실행한다() {
        List<String> calls = new ArrayList<>();

        AfterCommit.run(() -> calls.add("run"));

        assertEquals(List.of("run"), calls);
    }

    @Test
    void best_effort_작업이_실패해도_뒤에_등록된_커밋_후_콜백은_실행된다() {
        TransactionSynchronizationManager.initSynchronization();
        List<String> calls = new ArrayList<>();

        AfterCommit.runBestEffort("실패하는 정리", () -> {
            throw new IllegalStateException("redis down");
        });
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                calls.add("세션 폐기");
            }
        });

        assertDoesNotThrow(() -> TransactionSynchronizationUtils.triggerAfterCommit());
        assertEquals(List.of("세션 폐기"), calls);
    }
}
