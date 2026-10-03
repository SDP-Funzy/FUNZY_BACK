package com.sdp1617.backend.auth.migration;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

/** 실제 SQL(조건부 UPDATE)을 검증하기 위해 H2 메모리 DB(PostgreSQL 모드)를 쓴다. 동시성 충돌만 목으로 재현한다. */
class MemberEmailNormalizerTest {

    private JdbcTemplate jdbcTemplate;
    private MemberEmailNormalizer normalizer;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE members (id BIGINT PRIMARY KEY, email VARCHAR(255), provider VARCHAR(20) DEFAULT 'KAKAO', "
                + "CONSTRAINT uk_member_email UNIQUE (email))");
        normalizer = new MemberEmailNormalizer(jdbcTemplate);
    }

    private String emailOf(long id) {
        return jdbcTemplate.queryForObject("SELECT email FROM members WHERE id = ?", String.class, id);
    }

    @Test
    void 대소문자나_공백이_섞인_이메일을_정규화한다() {
        jdbcTemplate.update("INSERT INTO members (id, email) VALUES (1, 'John.Doe@Gmail.com'), (2, ' spaced@kakao.com'), (3, 'ok@naver.com'), (4, NULL)");

        normalizer.run(null);

        assertEquals("john.doe@gmail.com", emailOf(1));
        assertEquals("spaced@kakao.com", emailOf(2));
        assertEquals("ok@naver.com", emailOf(3));
    }

    @Test
    void 정규화하면_다른_회원과_겹치는_이메일은_건드리지_않는다() {
        // 대소문자만 다른 중복 계정: 어느 쪽을 남길지는 사람이 판단해야 하므로 그대로 둔다
        jdbcTemplate.update("INSERT INTO members (id, email) VALUES (1, 'dup@gmail.com'), (2, 'Dup@Gmail.com')");

        normalizer.run(null);

        assertEquals("dup@gmail.com", emailOf(1));
        assertEquals("Dup@Gmail.com", emailOf(2));
    }

    @Test
    void 정규화_안_된_회원끼리_겹쳐도_처리_순서로_한쪽이_이메일을_차지하지_않는다() {
        // 소문자 계정은 없고 대소문자만 다른 두 계정만 있는 경우 — 둘 다 그대로 두고 사람이 판단한다
        jdbcTemplate.update("INSERT INTO members (id, email) VALUES (1, 'Alice@Example.com'), (2, 'ALICE@EXAMPLE.COM'), (3, 'Solo@Example.com')");

        normalizer.run(null);

        assertEquals("Alice@Example.com", emailOf(1));
        assertEquals("ALICE@EXAMPLE.COM", emailOf(2));
        // 겹치지 않는 회원은 정상 정규화
        assertEquals("solo@example.com", emailOf(3));
    }

    @Test
    void 다시_실행해도_결과가_같다() {
        jdbcTemplate.update("INSERT INTO members (id, email) VALUES (1, 'Again@Gmail.com')");

        normalizer.run(null);
        normalizer.run(null);

        assertEquals("again@gmail.com", emailOf(1));
    }

    @Test
    void 탭이나_줄바꿈이_섞인_이메일도_조회와_같은_기준으로_정규화한다() {
        // SQL TRIM은 공백만 지우므로 SQL로 대상을 고르면 이런 행이 빠진다
        jdbcTemplate.update("INSERT INTO members (id, email) VALUES (1, ?), (2, ?)", "\tTab@Gmail.com", "newline@gmail.com\n");

        normalizer.run(null);

        assertEquals("tab@gmail.com", emailOf(1));
        assertEquals("newline@gmail.com", emailOf(2));
    }

    @Test
    void 공백뿐인_이메일은_NULL로_정리한다() {
        jdbcTemplate.update("INSERT INTO members (id, email) VALUES (1, '   ')");

        normalizer.run(null);

        assertNull(emailOf(1));
    }

    @Test
    void 정규화_중_유니크_제약_충돌이_나도_예외를_던지지_않고_다음_행을_처리한다() {
        // 러너가 도는 동안 같은 이메일로 가입이 커밋되면 조건부 UPDATE를 통과해도 유니크 제약에 걸릴 수 있다.
        // 이때 예외가 새면 ApplicationRunner 실패로 앱이 시작되지 않는다.
        // 실제 H2 테이블에서 대상 행을 읽고, UPDATE만 충돌하도록 감싼다
        jdbcTemplate.update("INSERT INTO members (id, email) VALUES (1, 'Race@Gmail.com'), (2, 'Next@Gmail.com')");
        JdbcTemplate failing = spy(jdbcTemplate);
        doThrow(new DuplicateKeyException("uk_member_email"))
                .when(failing).update(anyString(), eq("race@gmail.com"), eq(1L), eq("Race@Gmail.com"), eq("race@gmail.com"), eq(1L));

        assertDoesNotThrow(() -> new MemberEmailNormalizer(failing).run(null));

        assertEquals("Race@Gmail.com", emailOf(1));
        assertEquals("next@gmail.com", emailOf(2));
    }

    @Test
    void 공백뿐인_이메일이어도_아이디_비밀번호_회원은_NULL로_바꾸지_않는다() {
        // 이메일이 없으면 비밀번호 재설정·아이디 찾기를 할 수 없게 되므로 사람이 확인하도록 남겨둔다
        jdbcTemplate.update("INSERT INTO members (id, email, provider) VALUES (1, '  ', 'LOCAL')");

        normalizer.run(null);

        assertEquals("  ", emailOf(1));
    }

    @Test
    void 조회_이후_이메일이_바뀐_회원은_옛_값으로_덮어쓰지_않는다() {
        // 대상을 고른 뒤 UPDATE 전에 관리자 등이 이메일을 고친 상황
        jdbcTemplate.update("INSERT INTO members (id, email) VALUES (1, 'Old@Gmail.com')");
        JdbcTemplate changing = spy(jdbcTemplate);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            jdbcTemplate.update("UPDATE members SET email = 'fixed-by-admin@gmail.com' WHERE id = 1");
            return null;
        }).when(changing).query(anyString(), any(RowCallbackHandler.class));

        new MemberEmailNormalizer(changing).run(null);

        assertEquals("fixed-by-admin@gmail.com", emailOf(1));
    }

    @Test
    void 대상_조회부터_실패해도_예외를_던지지_않는다() {
        JdbcTemplate failing = spy(jdbcTemplate);
        doThrow(new DataAccessResourceFailureException("db down"))
                .when(failing).query(anyString(), any(RowCallbackHandler.class));

        assertDoesNotThrow(() -> new MemberEmailNormalizer(failing).run(null));
    }
}
