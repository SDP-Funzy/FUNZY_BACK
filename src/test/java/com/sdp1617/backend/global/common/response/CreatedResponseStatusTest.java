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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 생성 API는 응답 본문 code와 실제 HTTP 상태가 모두 201이고, Swagger 문서도 201로 표시한다 (#116).
 * ApiResponse.created를 쓰는 컨트롤러 메서드에는 @ResponseStatus(HttpStatus.CREATED)를 함께 붙인다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CreatedResponseStatusTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private JwtProvider jwtProvider;

    @Test
    void 생성_API는_HTTP_상태도_201이다() throws Exception {
        Member member = new Member("created@status.test", "encoded", "createdstatus", Consent.requiredOnly());
        em.persist(member);

        mockMvc.perform(post("/api/letters")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(member.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toName": "은우", "fromName": "티키", "designType": "DesignType_A"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("201"));
    }

    @Test
    void Swagger_문서도_생성_API를_201로_표시한다() throws Exception {
        String[][] createdOperations = {
                {"/api/auth/signup", "post"},
                {"/api/social/follow/requests", "post"},
                {"/api/letters", "post"},
                {"/api/letters/{letterId}/cards", "post"},
                {"/api/letters/{letterId}/reactions", "post"},
                {"/api/letters/{letterId}/comments", "post"},
                {"/api/letters/{letterId}/favorite", "post"},
                {"/api/heart-cards/{heartCardId}/phrase-comments", "post"},
                {"/api/archive/cards", "post"},
        };

        var result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        for (String[] operation : createdOperations) {
            String responses = "$.paths['" + operation[0] + "']." + operation[1] + ".responses";
            result.andExpect(jsonPath(responses + "['201']").exists())
                    .andExpect(jsonPath(responses + "['200']").doesNotExist());
        }
    }
}
