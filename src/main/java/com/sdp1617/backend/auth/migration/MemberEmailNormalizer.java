package com.sdp1617.backend.auth.migration;

import com.sdp1617.backend.auth.util.Emails;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 이메일 정규화(공백 제거 + 소문자, #101) 이전에 소셜 가입으로 대소문자가 섞인 채 저장된 이메일을 1회성으로 정규화한다.
 * 정규화하지 않으면 이메일 가입 중복 확인·아이디 찾기·잠금 해제 등 정규화된 값으로 조회하는 기능이 이 계정을 찾지 못한다.
 *
 * - 대상은 SQL이 아니라 {@link Emails#normalize}와 같은 기준으로 고른다. SQL TRIM은 공백만 지우고 Java trim은
 *   탭·줄바꿈까지 지우는 식으로 규칙이 달라, SQL로 고르면 조회에서는 정규화되는데 여기서는 빠지는 행이 생긴다.
 * - 공백뿐인 이메일은 소셜 회원만 NULL로 정리한다 (정규화 규칙상 빈 이메일은 "이메일 없음").
 *   아이디/비밀번호 회원은 이메일이 필수라 건드리지 않고 로그만 남긴다.
 * - 정규화하면 같은 이메일이 되는 회원이 둘 이상이면(대소문자만 다른 중복 계정) 그 그룹 전체를 덮어쓰지 않고
 *   로그만 남긴다 — 어느 계정을 남길지는 사람이 판단해야 한다. 이미 정규화된 회원이 있는 경우뿐 아니라
 *   정규화 안 된 회원끼리 겹치는 경우도 포함한다(처리 순서로 한쪽이 이메일을 차지하지 않도록). 조건부 UPDATE로 대부분 걸러지지만, 이 러너가 도는 동안 같은
 *   이메일로 가입이 커밋되는 경우까지 막지는 못하므로 유니크 제약 위반도 같은 경우로 처리한다.
 * - 어떤 DB 오류가 나도 앱 시작을 막지 않는다 (행 단위 오류는 그 행만 건너뛰고, 대상 조회 실패는 다음 시작에 재시도).
 *
 * Flyway/Liquibase가 없어 {@link SocialConnectionBackfiller}처럼 앱 시작 시점에 실행하며, 정규화할 행이 없으면
 * 아무것도 바꾸지 않으므로 매 시작마다 재실행돼도 안전(멱등)하다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MemberEmailNormalizer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            normalizeAll();
        } catch (DataAccessException exception) {
            // 대상 조회부터 실패해도 앱 시작은 막지 않는다 (다음 시작 때 다시 시도된다)
            log.error("회원 이메일 정규화 실행 실패 — 다음 시작 때 다시 시도", exception);
        }
    }

    private void normalizeAll() {
        // 바꿀 행만 모은다. 회원 전체를 한 번 훑는 비용은 남는다(PostgreSQL 드라이버는 결과를 한 번에 받아온다) —
        // 1회성 정리용이라 운영 반영 확인 후 이 러너를 제거한다(#108).
        List<Target> targets = new ArrayList<>();
        // 정규화하면 같은 이메일이 되는 회원 수 (이미 정규화된 회원 포함). 2명 이상이면 대소문자만 다른 중복 계정이다.
        Map<String, Integer> membersByNormalizedEmail = new HashMap<>();
        jdbcTemplate.query("SELECT id, email, provider FROM members WHERE email IS NOT NULL", (ResultSet resultSet) -> {
            String email = resultSet.getString("email");
            String normalizedEmail = Emails.normalize(email);
            if (normalizedEmail != null) {
                membersByNormalizedEmail.merge(normalizedEmail, 1, Integer::sum);
            }
            if (!Objects.equals(email, normalizedEmail)) {
                targets.add(new Target(resultSet.getLong("id"), email, normalizedEmail,
                        "LOCAL".equals(resultSet.getString("provider"))));
            }
        });
        int normalized = 0;
        int skipped = 0;
        for (Target target : targets) {
            // 중복 그룹은 처리 순서에 따라 먼저 처리된 계정이 이메일을 차지하지 않도록 그룹 전체를 건드리지 않는다.
            // (예: "Alice@x.com"과 "ALICE@x.com"만 있고 "alice@x.com"은 없는 경우 — 조건부 UPDATE만으로는 첫 번째가 바뀐다)
            if (target.normalizedEmail() != null && membersByNormalizedEmail.get(target.normalizedEmail()) > 1) {
                log.error("회원 이메일 정규화 건너뜀: memberId={} — 정규화하면 다른 회원과 같은 이메일(대소문자만 다른 중복 계정)", target.id());
                skipped++;
                continue;
            }
            if (normalizeOne(target)) {
                normalized++;
            } else {
                skipped++;
            }
        }
        if (normalized > 0) {
            log.info("회원 이메일 정규화 완료: {}건", normalized);
        }
        if (skipped > 0) {
            log.error("회원 이메일 정규화 중 {}건 건너뜀 — 위 로그의 memberId 수동 확인 필요", skipped);
        }
    }

    private record Target(long id, String originalEmail, String normalizedEmail, boolean local) {
    }

    private boolean normalizeOne(Target target) {
        long id = target.id();
        String normalizedEmail = target.normalizedEmail();
        try {
            if (normalizedEmail == null) {
                if (target.local()) {
                    // 아이디/비밀번호 회원은 이메일이 필수라 NULL로 바꾸지 않는다 (비밀번호 재설정·아이디 찾기 수단이 사라짐)
                    log.error("회원 이메일 정규화 건너뜀: memberId={} — 아이디/비밀번호 회원인데 이메일이 비어 있음", id);
                    return false;
                }
                // 조회한 뒤 이메일이 바뀌었으면(관리자 수정 등) 덮어쓰지 않도록 원래 값일 때만 바꾼다
                if (jdbcTemplate.update("UPDATE members SET email = NULL WHERE id = ? AND email = ?", id, target.originalEmail()) == 1) {
                    return true;
                }
                log.warn("회원 이메일 정규화 건너뜀: memberId={} — 조회 이후 변경되었거나 삭제됨", id);
                return false;
            }
            int updated = jdbcTemplate.update("""
                    UPDATE members SET email = ?
                    WHERE id = ? AND email = ?
                      AND NOT EXISTS (SELECT 1 FROM members other WHERE other.email = ? AND other.id <> ?)
                    """, normalizedEmail, id, target.originalEmail(), normalizedEmail, id);
            if (updated == 1) {
                return true;
            }
            // 0건인 이유는 둘 중 하나다: 다른 회원이 정규화 이메일을 쓰고 있거나, 조회 이후 이 회원이 변경·삭제됨
            log.error("회원 이메일 정규화 건너뜀: memberId={} — 정규화한 이메일을 다른 회원이 이미 사용 중(대소문자만 다른 중복 계정)이거나, 조회 이후 변경·삭제됨", id);
            return false;
        } catch (DuplicateKeyException exception) {
            // 이 UPDATE는 email만 바꾸므로 중복 키 위반은 이메일 유니크 제약뿐이다.
            // (JdbcTemplate 예외에는 Hibernate 예외가 없어 ConstraintViolations.nameOf로는 제약 이름을 알 수 없다)
            log.error("회원 이메일 정규화 건너뜀: memberId={} — 이메일 유니크 제약 충돌(정규화 중 같은 이메일로 가입이 들어온 경우 등)", id);
            return false;
        } catch (DataAccessException exception) {
            log.error("회원 이메일 정규화 실패: memberId={}", id, exception);
            return false;
        }
    }
}
