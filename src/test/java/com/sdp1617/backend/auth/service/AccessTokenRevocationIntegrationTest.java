package com.sdp1617.backend.auth.service;

import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.jwt.JwtProvider;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.mypage.service.AccountSettingsService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 비밀번호를 바꾸면 그 전에 로그인한 토큰은 바로 거절되고, 새로 로그인한 토큰은 쓸 수 있다 (#124). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccessTokenRevocationIntegrationTest {

    private static final String PASSWORD = "oldPassword1!";
    private static final String NEW_PASSWORD = "newPassword1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AccountSettingsService accountSettingsService;
    @Autowired private MemberRepository memberRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        member = new Member("revoke@token.test", passwordEncoder.encode(PASSWORD), "revoketoken", Consent.requiredOnly());
        em.persist(member);
    }

    private ResultActions callWith(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/mypage/notifications/unread-count")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    @Test
    void 비밀번호를_바꾸면_기존_토큰은_AUTH_027로_거절되고_새로_로그인한_토큰은_통과한다() throws Exception {
        String before = jwtProvider.createAccessToken(member.getId(), member.getSessionVersion());
        callWith(before).andExpect(status().isOk());

        accountSettingsService.changePassword(member.getId(), PASSWORD, NEW_PASSWORD, NEW_PASSWORD);
        em.flush();

        callWith(before)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_027"));
        em.refresh(member); // 새로 로그인: 바뀐 세션 버전을 읽는다
        callWith(jwtProvider.createAccessToken(member.getId(), member.getSessionVersion())).andExpect(status().isOk());
    }

    @Test
    void 세션을_끊는_요청이_겹쳐도_버전_증가가_사라지지_않는다() throws Exception {
        // 잠금 해제처럼 회원 행을 잠그지 않는 요청과 비밀번호 변경이 겹쳐도, 버전은 DB에서 올리므로 각각 반영된다
        memberRepository.incrementSessionVersion(member.getId()); // 다른 요청이 먼저 끊음
        String loggedInBetween = jwtProvider.createAccessToken(member.getId(), 1);

        accountSettingsService.changePassword(member.getId(), PASSWORD, NEW_PASSWORD, NEW_PASSWORD); // 버전 0을 읽은 상태
        em.flush();
        em.refresh(member);

        assertEquals(2, member.getSessionVersion());
        callWith(loggedInBetween)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_027"));
    }

    @Test
    void 변경이_커밋되기_전에_옛_상태로_로그인하거나_재발급한_토큰은_변경_뒤에_발급돼도_거절한다() throws Exception {
        // 변경과 동시에 진행된 로그인·재발급은 비밀번호와 함께 옛 세션 버전을 읽는다 — 발급 시각과 무관하게 무효
        int versionReadBeforeChange = member.getSessionVersion();

        accountSettingsService.changePassword(member.getId(), PASSWORD, NEW_PASSWORD, NEW_PASSWORD);
        em.flush();

        callWith(jwtProvider.createAccessToken(member.getId(), versionReadBeforeChange))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_027"));
    }

    @Test
    void 비밀번호_변경이_실패하면_기존_토큰은_그대로_쓸_수_있다() throws Exception {
        String before = jwtProvider.createAccessToken(member.getId(), member.getSessionVersion());

        try {
            accountSettingsService.changePassword(member.getId(), "wrongPassword1!", NEW_PASSWORD, NEW_PASSWORD);
        } catch (RuntimeException ignored) {
            // 현재 비밀번호 불일치(AUTH_015) — 세션을 끊지 않는다
        }
        em.flush();

        callWith(before).andExpect(status().isOk());
    }
}
