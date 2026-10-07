package com.sdp1617.backend.social.service;

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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

    private final MemberRepository memberRepository;
    private final FollowRequestRepository followRequestRepository;
    private final FollowRelationRepository followRelationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.social.max-follow-count}")
    private int maxFollowCount;

    public FollowCodeResponse getMyFollowCode(Long memberId) {
        Member member = getMember(memberId);
        return new FollowCodeResponse(member.getFollowCode());
    }

    @Transactional
    public FollowCodeResponse reissueFollowCode(Long memberId) {
        Member member = memberRepository.findActiveByIdForUpdate(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_002));
        member.reissueFollowCode();
        return new FollowCodeResponse(member.getFollowCode());
    }

    @Transactional
    public void sendFollowRequest(Long requesterId, String followCode) {
        Member receiver = memberRepository.findByFollowCode(followCode)
                .orElseThrow(() -> new CustomException(ErrorCode.SOCIAL_001));
        Long receiverId = receiver.getId();

        if (receiverId.equals(requesterId)) {
            throw new CustomException(ErrorCode.SOCIAL_002);
        }
        if (followRelationRepository.findBetween(requesterId, receiverId).isPresent()) {
            throw new CustomException(ErrorCode.SOCIAL_003);
        }
        if (followRequestRepository.existsByRequesterIdAndReceiverId(requesterId, receiverId)) {
            throw new CustomException(ErrorCode.SOCIAL_004);
        }

        // 상대가 이미 나에게 요청을 보낸 상태라면(교차 요청) 새 요청을 만들지 않고 바로 맞팔로 처리한다
        Optional<FollowRequest> reverseRequest =
                followRequestRepository.findByRequesterIdAndReceiverId(receiverId, requesterId);
        if (reverseRequest.isPresent()) {
            ensureUnderFollowLimit(requesterId, receiverId);
            followRequestRepository.delete(reverseRequest.get());
            followRelationRepository.save(FollowRelation.of(requesterId, receiverId));
            // 상대가 먼저 보낸 요청을 내가 수락한 것과 같다
            eventPublisher.publishEvent(new FollowAcceptedEvent(receiverId, requesterId));
            return;
        }

        if (followRelationRepository.countByMember(requesterId) >= maxFollowCount) {
            throw new CustomException(ErrorCode.SOCIAL_005);
        }
        followRequestRepository.save(new FollowRequest(requesterId, receiverId));
        eventPublisher.publishEvent(new FollowRequestedEvent(requesterId, receiverId));
    }

    public List<FollowRequestResponse> getReceivedRequests(Long memberId) {
        List<FollowRequest> requests = followRequestRepository.findByReceiverIdOrderByCreatedAtDesc(memberId);
        Map<Long, Member> requesterById = membersById(requests.stream().map(FollowRequest::getRequesterId).toList());

        return requests.stream()
                .filter(request -> requesterById.containsKey(request.getRequesterId()))
                .map(request -> {
                    Member requester = requesterById.get(request.getRequesterId());
                    return new FollowRequestResponse(
                            request.getId(), requester.getId(), requester.getNickname(), request.getCreatedAt());
                })
                .toList();
    }

    /** 내가 보낸, 아직 상대가 수락/거절하지 않은 요청 ("요청 중" 표시용). 최신순. */
    public List<SentFollowRequestResponse> getSentRequests(Long memberId) {
        List<FollowRequest> requests = followRequestRepository.findByRequesterIdOrderByCreatedAtDesc(memberId);
        Map<Long, Member> receiverById = membersById(requests.stream().map(FollowRequest::getReceiverId).toList());

        return requests.stream()
                .filter(request -> receiverById.containsKey(request.getReceiverId()))
                .map(request -> {
                    Member receiver = receiverById.get(request.getReceiverId());
                    return new SentFollowRequestResponse(request.getId(), receiver.getId(), receiver.getNickname(),
                            receiver.getProfileImageUrl(), request.getCreatedAt());
                })
                .toList();
    }

    /** 친구(맞팔) 목록. 최근에 친구가 된 순. 친구 수 상한이 있어 페이지네이션 없이 전부 반환한다. */
    public List<FriendResponse> getFriends(Long memberId) {
        List<FollowRelation> relations = followRelationRepository.findAllByMemberOrderByNewest(memberId);
        Map<Long, Member> friendById = membersById(relations.stream().map(r -> r.otherMemberId(memberId)).toList());

        return relations.stream()
                .filter(relation -> friendById.containsKey(relation.otherMemberId(memberId)))
                .map(relation -> {
                    Member friend = friendById.get(relation.otherMemberId(memberId));
                    return new FriendResponse(friend.getId(), friend.getNickname(), friend.getProfileImageUrl(),
                            relation.getCreatedAt());
                })
                .toList();
    }

    @Transactional
    public void acceptFollowRequest(Long memberId, Long requestId) {
        FollowRequest request = followRequestRepository.findById(requestId)
                .filter(r -> r.isReceivedBy(memberId))
                .orElseThrow(() -> new CustomException(ErrorCode.SOCIAL_006));

        ensureUnderFollowLimit(request.getRequesterId(), memberId);

        followRequestRepository.delete(request);
        followRelationRepository.save(FollowRelation.of(request.getRequesterId(), memberId));
        eventPublisher.publishEvent(new FollowAcceptedEvent(request.getRequesterId(), memberId));
    }

    @Transactional
    public void cancelFollowRequest(Long memberId, Long requestId) {
        FollowRequest request = followRequestRepository.findById(requestId)
                .filter(r -> r.isSentBy(memberId))
                .orElseThrow(() -> new CustomException(ErrorCode.SOCIAL_006));

        followRequestRepository.delete(request);
    }

    @Transactional
    public void rejectFollowRequest(Long memberId, Long requestId) {
        FollowRequest request = followRequestRepository.findById(requestId)
                .filter(r -> r.isReceivedBy(memberId))
                .orElseThrow(() -> new CustomException(ErrorCode.SOCIAL_006));

        followRequestRepository.delete(request);
    }

    @Transactional
    public void unfollow(Long memberId, Long followMemberId) {
        FollowRelation relation = followRelationRepository.findBetween(memberId, followMemberId)
                .orElseThrow(() -> new CustomException(ErrorCode.SOCIAL_007));

        followRelationRepository.delete(relation);
    }

    public FollowCountResponse getFollowCount(Long memberId) {
        return new FollowCountResponse(followRelationRepository.countByMember(memberId), maxFollowCount);
    }

    private void ensureUnderFollowLimit(Long memberId1, Long memberId2) {
        if (followRelationRepository.countByMember(memberId1) >= maxFollowCount
                || followRelationRepository.countByMember(memberId2) >= maxFollowCount) {
            throw new CustomException(ErrorCode.SOCIAL_005);
        }
    }

    /**
     * 회원 ID → 회원. 탈퇴로 회원이 사라졌는데 팔로우 요청/관계가 남은 경우(#93)는 맵에 없으므로,
     * 호출하는 쪽에서 걸러 목록 전체가 실패하지 않게 한다.
     */
    private Map<Long, Member> membersById(Collection<Long> memberIds) {
        return memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
    }

    private Member getMember(Long memberId) {
        return memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_002));
    }
}
