package com.sdp1617.backend.letter.controller;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.jwt.JwtProvider;
import com.sdp1617.backend.letter.dto.LetterEnvelopeRequest;
import com.sdp1617.backend.letter.entity.DesignType;
import com.sdp1617.backend.letter.entity.LetterCardContent;
import com.sdp1617.backend.letter.service.LetterWriteService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 카드 링크는 http(s)만 저장된다. javascript: 등은 받는 사람 브라우저에서 실행될 수 있다 (#155). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LetterCardLinkValidationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private LetterWriteService letterWriteService;

    private String bearer;
    private Long letterId;
    private Long cardId;

    @BeforeEach
    void setUp() {
        Member member = new Member("link@xss.test", "encoded", "linkxss", Consent.requiredOnly());
        em.persist(member);
        bearer = "Bearer " + jwtProvider.createAccessToken(member.getId(), member.getSessionVersion());
        letterId = letterWriteService.start(member.getId(),
                new LetterEnvelopeRequest("은우", "티키", DesignType.DesignType_A)).letterId();
        cardId = letterWriteService.addCard(member.getId(), letterId,
                        new LetterCardContent(ArchiveCategory.MUSIC, null, "내용", null, null, null, null))
                .cards().getFirst().cardId();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(document.cookie)",
            "JaVaScRiPt:alert(1)",
            "  javascript:alert(1)",
            "java\tscript:alert(1)",
            "\u0000javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "vbscript:msgbox(1)",
            "//evil.example.com",
            "https://",
            "https://example.com/a b",
            "https://example.com/a\u2003b"
    })
    void http_https가_아닌_링크는_카드_추가와_수정_모두_거절한다(String link) throws Exception {
        mockMvc.perform(withCard(post("/api/letters/{letterId}/cards", letterId), link))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
        mockMvc.perform(withCard(put("/api/letters/{letterId}/cards/{cardId}", letterId, cardId), link))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://music.example.com/track/1?t=30#start",
            "http://example.com",
            "HTTPS://EXAMPLE.COM",
            "  https://example.com  "
    })
    void http_https_링크는_저장한다(String link) throws Exception {
        mockMvc.perform(withCard(post("/api/letters/{letterId}/cards", letterId), link))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cards[1].link").value(link.trim()));
        mockMvc.perform(withCard(put("/api/letters/{letterId}/cards/{cardId}", letterId, cardId), link))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cards[0].link").value(link.trim()));
    }

    private MockHttpServletRequestBuilder withCard(MockHttpServletRequestBuilder request, String link) {
        return request.header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("category", "MUSIC", "content", "내용", "link", link)));
    }
}
