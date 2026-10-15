package com.sdp1617.backend.social.service;

import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.social.dto.FollowStatus;
import com.sdp1617.backend.social.dto.MemberProfileResponse;
import com.sdp1617.backend.social.entity.FollowRequest;
import com.sdp1617.backend.social.repository.FollowRequestRepository;
import com.sdp1617.backend.social.entity.FollowRelation;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 닉네임 검색 쿼리(앞부분 일치, LIKE 이스케이프, 친구 먼저)와 친구 관계 상태(#153)를 실제 DB로 검증한다 (CI에서는 PostgreSQL). */
@SpringBootTest
@Transactional
class MemberSearchIntegrationTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private MemberSearchService memberSearchService;

    @Autowired
    private FollowService followService;

    @Autowired
    private FollowRequestRepository followRequestRepository;

    private Member viewer;

    private Member member(String nickname) {
        Member member = new Member(nickname + "@search.test", "encoded", nickname, Consent.requiredOnly());
        em.persist(member);
        return member;
    }

    private List<String> search(String keyword) {
        return memberSearchService.searchByNickname(viewer.getId(), keyword).stream()
                .map(MemberProfileResponse::nickname)
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

        List<MemberProfileResponse> results = memberSearchService.searchByNickname(viewer.getId(), "ZQ");

        assertEquals(List.of("zqzulu", "zqalpha"), results.stream().map(MemberProfileResponse::nickname).toList());
        assertEquals(List.of(FollowStatus.FRIEND, FollowStatus.NONE),
                results.stream().map(MemberProfileResponse::followStatus).toList());
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

    private Long requestId(Member requester, Member receiver) {
        return followRequestRepository.findByRequesterIdAndReceiverId(requester.getId(), receiver.getId())
                .map(FollowRequest::getId).orElseThrow();
    }

    private MemberProfileResponse profile(Member target) {
        em.flush();
        return memberSearchService.getProfile(viewer.getId(), target.getId());
    }

    @Test
    void 검색_결과에_요청함_받은_요청_상태와_요청_ID가_나온다() {
        Member requested = member("zqasked");
        Member received = member("zqbeggar");
        member("zqcalm");
        followService.sendFollowRequest(viewer.getId(), requested.getId());
        followService.sendFollowRequest(received.getId(), viewer.getId());
        em.flush();

        List<MemberProfileResponse> results = memberSearchService.searchByNickname(viewer.getId(), "zq");

        assertEquals(List.of("zqasked", "zqbeggar", "zqcalm"), results.stream().map(MemberProfileResponse::nickname).toList());
        assertEquals(List.of(FollowStatus.REQUESTED, FollowStatus.RECEIVED, FollowStatus.NONE),
                results.stream().map(MemberProfileResponse::followStatus).toList());
        assertEquals(requestId(viewer, requested), results.get(0).followRequestId());
        assertEquals(requestId(received, viewer), results.get(1).followRequestId());
        assertNull(results.get(2).followRequestId());
    }

    @Test
    void 프로필_관계_상태는_요청_취소_수락_끊기에_따라_바뀐다() {
        Member other = member("zqother");
        assertEquals(FollowStatus.NONE, profile(other).followStatus());
        assertEquals(FollowStatus.SELF, profile(viewer).followStatus());

        followService.sendFollowRequest(viewer.getId(), other.getId());
        MemberProfileResponse requested = profile(other);
        assertEquals(FollowStatus.REQUESTED, requested.followStatus());
        assertEquals(requestId(viewer, other), requested.followRequestId());

        followService.cancelFollowRequest(viewer.getId(), requested.followRequestId());
        assertEquals(FollowStatus.NONE, profile(other).followStatus());

        followService.sendFollowRequest(other.getId(), viewer.getId());
        MemberProfileResponse received = profile(other);
        assertEquals(FollowStatus.RECEIVED, received.followStatus());

        followService.acceptFollowRequest(viewer.getId(), received.followRequestId());
        MemberProfileResponse friend = profile(other);
        assertEquals(FollowStatus.FRIEND, friend.followStatus());
        assertNull(friend.followRequestId());
        assertEquals("zqother", friend.nickname());

        followService.unfollow(viewer.getId(), other.getId());
        assertEquals(FollowStatus.NONE, profile(other).followStatus());
    }

    @Test
    void 받은_요청을_거절하면_다시_요청_가능_상태가_된다() {
        Member other = member("zqother");
        followService.sendFollowRequest(other.getId(), viewer.getId());
        followService.rejectFollowRequest(viewer.getId(), profile(other).followRequestId());

        assertEquals(FollowStatus.NONE, profile(other).followStatus());
    }

    @Test
    void 탈퇴했거나_없는_회원의_프로필은_없는_회원과_같다() {
        Member withdrawn = member("zqgone");
        em.flush();
        withdrawn.withdraw();
        em.flush();

        assertEquals(ErrorCode.SOCIAL_001, assertThrows(CustomException.class, () -> profile(withdrawn)).getErrorCode());
        assertEquals(ErrorCode.SOCIAL_001, assertThrows(CustomException.class,
                () -> memberSearchService.getProfile(viewer.getId(), -1L)).getErrorCode());
    }

    @Test
    void 교차_요청이_둘_다_남아_있어도_검색과_프로필이_같은_상태를_보인다() {
        // 동시에 서로 요청하면 교차 요청 확인을 둘 다 통과해 양쪽 요청이 다 저장될 수 있다
        Member other = member("zqcross");
        FollowRequest mine = new FollowRequest(viewer.getId(), other.getId());
        em.persist(mine);
        em.persist(new FollowRequest(other.getId(), viewer.getId()));
        em.flush();

        MemberProfileResponse searched = memberSearchService.searchByNickname(viewer.getId(), "zqc").get(0);
        MemberProfileResponse profiled = profile(other);

        assertEquals(FollowStatus.REQUESTED, searched.followStatus());
        assertEquals(mine.getId(), searched.followRequestId());
        assertEquals(searched, profiled);
    }

    @Test
    void 나와_친구의_프로필은_요청_수를_세지_않는다() {
        Member friend = member("zqfriend");
        em.persist(FollowRelation.of(viewer.getId(), friend.getId()));
        em.flush();

        for (int i = 0; i < 70; i++) {
            assertEquals(FollowStatus.FRIEND, profile(friend).followStatus());
            assertEquals(FollowStatus.SELF, profile(viewer).followStatus());
        }
    }
}
