package com.sdp1617.backend.global.common.response;

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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 성공 응답은 생성 API를 포함해 모두 HTTP 200, 본문 code "200"이고 Swagger 문서도 200으로만 표시한다 (#116). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SuccessResponseStatusTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private JwtProvider jwtProvider;

    @Test
    void 생성_API도_HTTP_200과_code_200으로_응답한다() throws Exception {
        Member member = new Member("success@status.test", "encoded", "successstatus", Consent.requiredOnly());
        em.persist(member);

        mockMvc.perform(post("/api/letters")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(member.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toName": "은우", "fromName": "티키", "designType": "DesignType_A"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));
    }

    @Test
    void Swagger_문서에_201_응답이나_code_201_예시가_없다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths..responses['201']").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/signup'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/social/follow/requests'].post.responses['200']").exists())
                // 응답 예시는 JSON 객체로 나온다: 예시 하나(example)와 여러 개(examples.*.value) 모두 확인
                .andExpect(jsonPath("$.paths..example.code", hasItem("200")))
                .andExpect(jsonPath("$.paths..example.code", not(hasItem("201"))))
                .andExpect(jsonPath("$.paths..examples..value.code", not(hasItem("201"))));
    }
}
