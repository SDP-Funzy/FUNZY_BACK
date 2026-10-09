package com.sdp1617.backend.letter.controller;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.jwt.JwtProperties;
import com.sdp1617.backend.auth.jwt.JwtProvider;
import com.sdp1617.backend.letter.dto.LetterCardRequest;
import com.sdp1617.backend.letter.dto.LetterEnvelopeRequest;
import com.sdp1617.backend.letter.entity.DesignType;
import com.sdp1617.backend.letter.service.LetterCardContentResolver;
import com.sdp1617.backend.letter.service.LetterShareService;
import com.sdp1617.backend.letter.service.LetterWriteService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 공유 링크 열람은 로그인 없이, 받기는 로그인해야 한다 (#87, #114, LR-030~032). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LetterShareApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager em;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private LetterWriteService letterWriteService;
    @Autowired private LetterCardContentResolver contentResolver;
    @Autowired private LetterShareService letterShareService;

    private Member sender;
    private Member receiver;
    private String token;

    private Member member(String name) {
        Member member = new Member(name + "@shareapi.test", "encoded", name, Consent.requiredOnly());
        em.persist(member);
        return member;
    }

    private String bearer(Member member) {
        return "Bearer " + jwtProvider.createAccessToken(member.getId(), member.getSessionVersion());
    }

    @BeforeEach
    void setUp() {
        sender = member("shareapisender");
        receiver = member("shareapireceiver");
        Long letterId = letterWriteService.start(sender.getId(),
                new LetterEnvelopeRequest("은우", "티키", DesignType.DesignType_A)).letterId();
        letterWriteService.addCard(sender.getId(), letterId, contentResolver.resolve(sender.getId(),
                new LetterCardRequest(ArchiveCategory.MUSIC, null, "링크로 보내는 편지", null, null, null)));
        letterWriteService.complete(sender.getId(), letterId);
        token = letterShareService.issue(sender.getId(), letterId).token();
    }

    @Test
    void 로그인_없이_링크로_편지를_읽을_수_있다() throws Exception {
        mockMvc.perform(get("/api/letters/shared/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewer").value("ANONYMOUS"))
                .andExpect(jsonPath("$.data.receivable").value(false))
                .andExpect(jsonPath("$.data.letter.cards[0].content").value("링크로 보내는 편지"));
    }

    @Test
    void 로그인하지_않고_받으면_401이고_로그인하면_받는_사람이_된다() throws Exception {
        mockMvc.perform(post("/api/letters/shared/{token}/receive", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_003"));

        mockMvc.perform(get("/api/letters/shared/{token}", token).header(HttpHeaders.AUTHORIZATION, bearer(receiver)))
                .andExpect(jsonPath("$.data.viewer").value("OTHER"))
                .andExpect(jsonPath("$.data.receivable").value(true));
        mockMvc.perform(post("/api/letters/shared/{token}/receive", token).header(HttpHeaders.AUTHORIZATION, bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipient.memberId").value(receiver.getId()));
        mockMvc.perform(get("/api/letters/shared/{token}", token).header(HttpHeaders.AUTHORIZATION, bearer(receiver)))
                .andExpect(jsonPath("$.data.viewer").value("RECIPIENT"));
    }

    @Test
    void 링크_발급과_취소는_로그인해야_한다() throws Exception {
        mockMvc.perform(post("/api/letters/{letterId}/share-link", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 토큰을_보냈는데_만료_무효면_익명으로_넘기지_않고_401이다() throws Exception {
        String expired = new JwtProvider(new JwtProperties(jwtProperties.secret(), -1_000L, -1_000L))
                .createAccessToken(receiver.getId(), receiver.getSessionVersion());
        mockMvc.perform(get("/api/letters/shared/{token}", token).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
        mockMvc.perform(get("/api/letters/shared/{token}", token).header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
    }

    @Test
    void 없는_링크는_LETTER_011() throws Exception {
        mockMvc.perform(get("/api/letters/shared/{token}", "no-such-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LETTER_011"));
    }
}
