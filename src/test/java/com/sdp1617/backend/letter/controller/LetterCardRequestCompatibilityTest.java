package com.sdp1617.backend.letter.controller;

import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.jwt.JwtProvider;
import com.sdp1617.backend.letter.dto.LetterEnvelopeRequest;
import com.sdp1617.backend.letter.entity.DesignType;
import com.sdp1617.backend.letter.service.LetterWriteService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 카드 제목을 없앤 뒤(#113)에도 예전 앱이 보내는 title은 무시되고 카드가 저장된다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LetterCardRequestCompatibilityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private LetterWriteService letterWriteService;

    @Test
    void 예전_앱이_title을_보내도_무시하고_카드를_추가한다() throws Exception {
        Member member = new Member("title@compat.test", "encoded", "titlecompat", Consent.requiredOnly());
        em.persist(member);
        Long letterId = letterWriteService.start(member.getId(),
                new LetterEnvelopeRequest("은우", "티키", DesignType.DesignType_A)).letterId();

        mockMvc.perform(post("/api/letters/{letterId}/cards", letterId)
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtProvider.createAccessToken(member.getId(), member.getSessionVersion()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category": "MUSIC", "title": "예전 앱이 보내는 제목", "content": "내용"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cards[0].content").value("내용"))
                .andExpect(jsonPath("$.data.cards[0].title").doesNotExist());
    }
}
