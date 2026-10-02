package com.sdp1617.backend.auth.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 로그인 실패 횟수가 DB(members.failed_login_count)에서 Redis로 옮겨가면서(#76) 엔티티에서 빠진 컬럼을 지운다.
 * ddl-auto: update는 컬럼을 추가만 하고 지우지 않는데, failed_login_count는 기본값 없는 NOT NULL이라
 * 남겨두면 이후 회원 INSERT가 전부 실패한다. Flyway/Liquibase가 없어 {@link SocialConnectionBackfiller}처럼
 * 앱 시작 시점에 처리한다. PostgreSQL의 ALTER TABLE은 컬럼이 없어도 테이블 전체 잠금(ACCESS EXCLUSIVE)부터
 * 잡으므로, 매 시작마다 잠그지 않도록 컬럼이 실제로 있을 때만 실행한다(멱등).
 * (last_failed_login_at은 #76 작업 중에만 잠깐 있었던 컬럼으로, 로컬 DB에 남아 있을 수 있어 함께 정리)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginLockColumnDropper implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        dropIfExists("failed_login_count");
        dropIfExists("last_failed_login_at");
    }

    private void dropIfExists(String column) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE LOWER(table_schema) = LOWER(CURRENT_SCHEMA)
                  AND LOWER(table_name) = 'members' AND LOWER(column_name) = ?
                """, Integer.class, column);
        if (count != null && count > 0) {
            jdbcTemplate.execute("ALTER TABLE members DROP COLUMN IF EXISTS " + column);
            log.info("members.{} 컬럼 제거", column);
        }
    }
}
