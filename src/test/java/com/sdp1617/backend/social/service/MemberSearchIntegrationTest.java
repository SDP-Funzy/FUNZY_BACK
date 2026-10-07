package com.sdp1617.backend.social.service;

import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.social.dto.MemberSearchResponse;
import com.sdp1617.backend.social.entity.FollowRelation;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 닉네임 검색 쿼리(앞부분 일치, LIKE 이스케이프, 친구 먼저)를 실제 DB로 검증한다 (CI에서는 PostgreSQL). */
@SpringBootTest
@Transactional
class MemberSearchIntegrationTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private MemberSearchService memberSearchService;

    private Member viewer;

    private Member member(String nickname) {
        Member member = new Member(nickname + "@search.test", "encoded", nickname, Consent.requiredOnly());
        em.persist(member);
        return member;
    }

    private List<String> search(String keyword) {
        return memberSearchService.searchByNickname(viewer.getId(), keyword).stream()
                .map(MemberSearchResponse::nickname)
                .toList();
    }

    @BeforeEach
    void setUp() {
        viewer = member("zqviewer");
    }

    @Test
    void 앞부분이_일치하는_회원을_친구_먼저_보여준다() {
        member("zqalpha");
        Member friend = member("zqzulu");
        member("xzqnotprefix");
        em.persist(FollowRelation.of(viewer.getId(), friend.getId()));
        em.flush();

        List<MemberSearchResponse> results = memberSearchService.searchByNickname(viewer.getId(), "ZQ");

        assertEquals(List.of("zqzulu", "zqalpha"), results.stream().map(MemberSearchResponse::nickname).toList());
        assertEquals(List.of(true, false), results.stream().map(MemberSearchResponse::friend).toList());
    }

    @Test
    void 나와_탈퇴한_회원은_나오지_않는다() {
        Member withdrawn = member("zqgone");
        em.flush();
        withdrawn.withdraw();
        em.flush();

        assertEquals(List.of(), search("zqv"));
        assertEquals(List.of(), search("zqg"));
    }

    @Test
    void 밑줄과_퍼센트는_글자_그대로_찾는다() {
        member("zq_under");
        member("zqxunder");
        em.flush();

        assertEquals(List.of("zq_under"), search("zq_"));
        assertEquals(List.of(), search("zq%"));
    }

    @Test
    void 검색어가_2자_미만이면_거절한다() {
        CustomException exception = assertThrows(CustomException.class, () -> search(" z "));

        assertEquals(ErrorCode.SOCIAL_008, exception.getErrorCode());
    }
}
