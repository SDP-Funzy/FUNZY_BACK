package com.sdp1617.backend.social.service;

import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.social.dto.FollowCodeResponse;
import com.sdp1617.backend.social.dto.FollowCountResponse;
import com.sdp1617.backend.social.dto.FollowRequestResponse;
import com.sdp1617.backend.social.dto.FriendResponse;
import com.sdp1617.backend.social.dto.SentFollowRequestResponse;
import com.sdp1617.backend.social.entity.FollowRelation;
import com.sdp1617.backend.social.entity.FollowRequest;
import com.sdp1617.backend.social.repository.FollowRelationRepository;
import com.sdp1617.backend.social.repository.FollowRequestRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private FollowRequestRepository followRequestRepository;

    @Mock
    private FollowRelationRepository followRelationRepository;

    @InjectMocks
    private FollowService followService;

    @BeforeEach
    void setUp() throws Exception {
        Field field = FollowService.class.getDeclaredField("maxFollowCount");
        field.setAccessible(true);
        field.set(followService, 2);
    }

    private Member member(Long id, String nickname) {
        Member member = new Member(id + "@sdp1617.com", "encoded", nickname, Consent.requiredOnly());
        setId(member, Member.class, id);
        return member;
    }

    private void setId(Object entity, Class<?> type, Long id) {
        try {
            Field field = type.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void 내_팔로우코드를_조회한다() {
        Member member = member(1L, "닉네임");
        when(memberRepository.findActiveById(1L)).thenReturn(Optional.of(member));

        FollowCodeResponse response = followService.getMyFollowCode(1L);

        assertEquals(member.getFollowCode(), response.followCode());
    }

    @Test
    void 팔로우코드로_요청을_보낸다() {
        Member receiver = member(2L, "받는사람");
        when(memberRepository.findByFollowCode("CODE1234")).thenReturn(Optional.of(receiver));
        when(followRelationRepository.findBetween(1L, 2L)).thenReturn(Optional.empty());
        when(followRequestRepository.existsByRequesterIdAndReceiverId(1L, 2L)).thenReturn(false);
        when(followRequestRepository.findByRequesterIdAndReceiverId(2L, 1L)).thenReturn(Optional.empty());
        when(followRelationRepository.countByMember(1L)).thenReturn(0L);

        followService.sendFollowRequest(1L, "CODE1234");

        ArgumentCaptor<FollowRequest> captor = ArgumentCaptor.forClass(FollowRequest.class);
        verify(followRequestRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getRequesterId());
        assertEquals(2L, captor.getValue().getReceiverId());
    }

    @Test
    void 상대가_이미_나에게_요청했으면_바로_맞팔_처리된다() {
        Member receiver = member(2L, "받는사람");
        FollowRequest reverseRequest = new FollowRequest(2L, 1L);
        when(memberRepository.findByFollowCode("CODE1234")).thenReturn(Optional.of(receiver));
        when(followRelationRepository.findBetween(1L, 2L)).thenReturn(Optional.empty());
        when(followRequestRepository.existsByRequesterIdAndReceiverId(1L, 2L)).thenReturn(false);
        when(followRequestRepository.findByRequesterIdAndReceiverId(2L, 1L)).thenReturn(Optional.of(reverseRequest));
        when(followRelationRepository.countByMember(1L)).thenReturn(0L);
        when(followRelationRepository.countByMember(2L)).thenReturn(0L);

        followService.sendFollowRequest(1L, "CODE1234");

        verify(followRequestRepository).delete(reverseRequest);
        verify(followRequestRepository, never()).save(any());
        ArgumentCaptor<FollowRelation> captor = ArgumentCaptor.forClass(FollowRelation.class);
        verify(followRelationRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getMemberIdA());
        assertEquals(2L, captor.getValue().getMemberIdB());
    }

    @Test
    void 존재하지_않는_코드로_요청하면_SOCIAL_001_예외를_던진다() {
        when(memberRepository.findByFollowCode("BADCODE")).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class,
                () -> followService.sendFollowRequest(1L, "BADCODE"));

        assertEquals(ErrorCode.SOCIAL_001, exception.getErrorCode());
    }

    @Test
    void 본인_코드로_요청하면_SOCIAL_002_예외를_던진다() {
        Member self = member(1L, "닉네임");
        when(memberRepository.findByFollowCode("CODE1234")).thenReturn(Optional.of(self));

        CustomException exception = assertThrows(CustomException.class,
                () -> followService.sendFollowRequest(1L, "CODE1234"));

        assertEquals(ErrorCode.SOCIAL_002, exception.getErrorCode());
    }

    @Test
    void 이미_친구인_회원에게_요청하면_SOCIAL_003_예외를_던진다() {
        Member receiver = member(2L, "받는사람");
        when(memberRepository.findByFollowCode("CODE1234")).thenReturn(Optional.of(receiver));
        when(followRelationRepository.findBetween(1L, 2L)).thenReturn(Optional.of(FollowRelation.of(1L, 2L)));

        CustomException exception = assertThrows(CustomException.class,
                () -> followService.sendFollowRequest(1L, "CODE1234"));

        assertEquals(ErrorCode.SOCIAL_003, exception.getErrorCode());
    }

    @Test
    void 이미_보낸_요청이_있으면_SOCIAL_004_예외를_던진다() {
        Member receiver = member(2L, "받는사람");
        when(memberRepository.findByFollowCode("CODE1234")).thenReturn(Optional.of(receiver));
        when(followRelationRepository.findBetween(1L, 2L)).thenReturn(Optional.empty());
        when(followRequestRepository.existsByRequesterIdAndReceiverId(1L, 2L)).thenReturn(true);

        CustomException exception = assertThrows(CustomException.class,
                () -> followService.sendFollowRequest(1L, "CODE1234"));

        assertEquals(ErrorCode.SOCIAL_004, exception.getErrorCode());
    }

    @Test
    void 친구수_상한을_초과하면_요청시_SOCIAL_005_예외를_던진다() {
        Member receiver = member(2L, "받는사람");
        when(memberRepository.findByFollowCode("CODE1234")).thenReturn(Optional.of(receiver));
        when(followRelationRepository.findBetween(1L, 2L)).thenReturn(Optional.empty());
        when(followRequestRepository.existsByRequesterIdAndReceiverId(1L, 2L)).thenReturn(false);
        when(followRequestRepository.findByRequesterIdAndReceiverId(2L, 1L)).thenReturn(Optional.empty());
        when(followRelationRepository.countByMember(1L)).thenReturn(2L);

        CustomException exception = assertThrows(CustomException.class,
                () -> followService.sendFollowRequest(1L, "CODE1234"));

        assertEquals(ErrorCode.SOCIAL_005, exception.getErrorCode());
    }

    @Test
    void 받은_요청_목록을_조회한다() {
        FollowRequest request = new FollowRequest(2L, 1L);
        setId(request, FollowRequest.class, 100L);
        when(followRequestRepository.findByReceiverIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(request));
        when(memberRepository.findAllById(List.of(2L))).thenReturn(List.of(member(2L, "요청자")));

        List<FollowRequestResponse> responses = followService.getReceivedRequests(1L);

        assertEquals(1, responses.size());
        assertEquals("요청자", responses.get(0).requesterNickname());
    }

    @Test
    void 요청을_수락하면_팔로우관계가_생성되고_요청은_삭제된다() {
        FollowRequest request = new FollowRequest(2L, 1L);
        setId(request, FollowRequest.class, 100L);
        when(followRequestRepository.findById(100L)).thenReturn(Optional.of(request));
        when(followRelationRepository.countByMember(2L)).thenReturn(0L);
        when(followRelationRepository.countByMember(1L)).thenReturn(0L);

        followService.acceptFollowRequest(1L, 100L);

        verify(followRequestRepository).delete(request);
        ArgumentCaptor<FollowRelation> captor = ArgumentCaptor.forClass(FollowRelation.class);
        verify(followRelationRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getMemberIdA());
        assertEquals(2L, captor.getValue().getMemberIdB());
    }

    @Test
    void 내가_받지_않은_요청을_수락하면_SOCIAL_006_예외를_던진다() {
        FollowRequest request = new FollowRequest(2L, 3L);
        setId(request, FollowRequest.class, 100L);
        when(followRequestRepository.findById(100L)).thenReturn(Optional.of(request));

        CustomException exception = assertThrows(CustomException.class,
                () -> followService.acceptFollowRequest(1L, 100L));

        assertEquals(ErrorCode.SOCIAL_006, exception.getErrorCode());
        verify(followRelationRepository, never()).save(any());
    }

    @Test
    void 요청을_거절하면_요청만_삭제된다() {
        FollowRequest request = new FollowRequest(2L, 1L);
        setId(request, FollowRequest.class, 100L);
        when(followRequestRepository.findById(100L)).thenReturn(Optional.of(request));

        followService.rejectFollowRequest(1L, 100L);

        verify(followRequestRepository).delete(request);
        verify(followRelationRepository, never()).save(any());
    }

    @Test
    void 팔로우를_끊는다() {
        FollowRelation relation = FollowRelation.of(1L, 2L);
        when(followRelationRepository.findBetween(1L, 2L)).thenReturn(Optional.of(relation));

        followService.unfollow(1L, 2L);

        verify(followRelationRepository).delete(relation);
    }

    @Test
    void 친구가_아닌_상대를_끊으려하면_SOCIAL_007_예외를_던진다() {
        when(followRelationRepository.findBetween(1L, 2L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class,
                () -> followService.unfollow(1L, 2L));

        assertEquals(ErrorCode.SOCIAL_007, exception.getErrorCode());
    }

    @Test
    void 친구수와_상한을_조회한다() {
        when(followRelationRepository.countByMember(1L)).thenReturn(1L);

        FollowCountResponse response = followService.getFollowCount(1L);

        assertEquals(1L, response.currentCount());
        assertEquals(2, response.maxCount());
    }

    @Test
    void 친구_목록은_관계의_어느_쪽이든_상대_회원으로_조회한다() {
        // 1번 회원 기준: (1,3)에서는 3번이, (1,2)에서는 2번이 친구다. 저장소가 최신순으로 준 순서를 유지한다.
        FollowRelation newer = FollowRelation.of(3L, 1L);
        FollowRelation older = FollowRelation.of(1L, 2L);
        when(followRelationRepository.findAllByMemberOrderByNewest(1L)).thenReturn(List.of(newer, older));
        when(memberRepository.findAllById(List.of(3L, 2L))).thenReturn(List.of(member(2L, "친구2"), member(3L, "친구3")));

        List<FriendResponse> friends = followService.getFriends(1L);

        assertEquals(List.of(3L, 2L), friends.stream().map(FriendResponse::memberId).toList());
        assertEquals("친구3", friends.get(0).nickname());
    }

    @Test
    void 탈퇴해_회원이_없는_친구는_목록에서_빠진다() {
        when(followRelationRepository.findAllByMemberOrderByNewest(1L))
                .thenReturn(List.of(FollowRelation.of(1L, 2L), FollowRelation.of(1L, 9L)));
        when(memberRepository.findAllById(List.of(2L, 9L))).thenReturn(List.of(member(2L, "친구2")));

        List<FriendResponse> friends = followService.getFriends(1L);

        assertEquals(List.of(2L), friends.stream().map(FriendResponse::memberId).toList());
    }

    @Test
    void 탈퇴한_회원이_보낸_요청은_받은_요청_목록에서_빠진다() {
        FollowRequest fromWithdrawn = new FollowRequest(9L, 1L);
        setId(fromWithdrawn, FollowRequest.class, 100L);
        when(followRequestRepository.findByReceiverIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(fromWithdrawn));
        when(memberRepository.findAllById(List.of(9L))).thenReturn(List.of());

        assertEquals(0, followService.getReceivedRequests(1L).size());
    }

    @Test
    void 보낸_요청_목록을_조회한다() {
        FollowRequest request = new FollowRequest(1L, 2L);
        setId(request, FollowRequest.class, 100L);
        when(followRequestRepository.findByRequesterIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(request));
        when(memberRepository.findAllById(List.of(2L))).thenReturn(List.of(member(2L, "받는사람")));

        List<SentFollowRequestResponse> responses = followService.getSentRequests(1L);

        assertEquals(1, responses.size());
        assertEquals(100L, responses.get(0).requestId());
        assertEquals("받는사람", responses.get(0).receiverNickname());
    }

    @Test
    void 내가_보낸_요청을_취소한다() {
        FollowRequest request = new FollowRequest(1L, 2L);
        when(followRequestRepository.findById(100L)).thenReturn(Optional.of(request));

        followService.cancelFollowRequest(1L, 100L);

        verify(followRequestRepository).delete(request);
    }

    @Test
    void 내가_보내지_않은_요청을_취소하면_SOCIAL_006_예외를_던진다() {
        // 나에게 온 요청(받은 요청)은 취소가 아니라 거절해야 한다
        FollowRequest request = new FollowRequest(2L, 1L);
        when(followRequestRepository.findById(100L)).thenReturn(Optional.of(request));

        CustomException exception = assertThrows(CustomException.class,
                () -> followService.cancelFollowRequest(1L, 100L));

        assertEquals(ErrorCode.SOCIAL_006, exception.getErrorCode());
        verify(followRequestRepository, never()).delete(any());
    }
}
