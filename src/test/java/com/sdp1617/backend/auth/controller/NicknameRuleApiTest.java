package com.sdp1617.backend.auth.controller;

import com.sdp1617.backend.auth.dto.NicknamePolicy;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.jwt.JwtProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 아이디 규칙(#144)은 중복 확인에도 적용하고, 로그인에는 적용하지 않는다 (규칙 전에 가입한 회원 보호). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NicknameRuleApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private JwtProvider jwtProvider;

    @Test
    void 중복_확인은_규칙에_맞는_아이디만_조회하고_어긋나면_규칙을_안내한다() throws Exception {
        mockMvc.perform(get("/api/auth/nickname/check").param("nickname", "tiki.kim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));

        mockMvc.perform(get("/api/auth/nickname/check").param("nickname", "Tiki"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.message").value(NicknamePolicy.MESSAGE));
    }

    @Test
    void 중복_확인도_가입처럼_앞뒤_공백을_자르고_검사한다() throws Exception {
        mockMvc.perform(get("/api/auth/nickname/check").param("nickname", " tiki.kim "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));

        mockMvc.perform(get("/api/auth/nickname/check").param("nickname", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.message").value(NicknamePolicy.REQUIRED_MESSAGE));
    }

    @Test
    void 로그인한_회원이_중복_확인하면_본인을_빼고_확인한다() throws Exception {
        Member legacy = new Member("legacy@rule.test", "encoded", "RuleLegacy", Consent.requiredOnly());
        em.persist(legacy);
        em.flush();

        mockMvc.perform(get("/api/auth/nickname/check").param("nickname", "rulelegacy")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtProvider.createAccessToken(legacy.getId(), legacy.getSessionVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));

        mockMvc.perform(get("/api/auth/nickname/check").param("nickname", "rulelegacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void 로그인은_규칙과_관계없이_예전_아이디로_시도할_수_있다() throws Exception {
        // 규칙 검사로 400이 나지 않고, 없는 아이디라 로그인 실패(AUTH_001)까지 간다
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "예전한글아이디", "password": "Password1!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }
}
