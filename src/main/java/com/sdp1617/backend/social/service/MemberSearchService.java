package com.sdp1617.backend.social.service;

import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.social.dto.FollowStatus;
import com.sdp1617.backend.social.dto.MemberProfileResponse;
import com.sdp1617.backend.social.entity.FollowRequest;
import com.sdp1617.backend.social.repository.FollowRelationRepository;
import com.sdp1617.backend.social.repository.FollowRequestRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 닉네임(아이디)으로 회원 찾기 (LW-820, FR-030). 앞부분이 일치하는 회원을 친구 먼저 최대 20명까지 보여준다.
 * 닉네임은 로그인 아이디이기도 해서, 2자 이상부터 검색하고 결과 수·요청 수를 제한해 아이디 목록을 대량으로 모으지 못하게 한다.
 * 나 자신과 탈퇴한 회원은 나오지 않는다.
 * 회원 ID로 프로필·관계 상태 조회(#153)도 친구가 아닌 회원 조회는 요청 수를 따로 세서, 순번 ID를 돌며 아이디를 모으지 못하게 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberSearchService {

    static final int MIN_KEYWORD_LENGTH = 2;
    static final int MAX_RESULTS = 20;

    private final MemberRepository memberRepository;
    private final MemberSearchRateLimiter rateLimiter;
    private final FollowRelationRepository followRelationRepository;
    private final FollowRequestRepository followRequestRepository;

    public List<MemberProfileResponse> searchByNickname(Long viewerId, String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.length() < MIN_KEYWORD_LENGTH) {
            throw new CustomException(ErrorCode.SOCIAL_008);
        }
        if (!rateLimiter.tryAcquire(viewerId)) {
            throw new CustomException(ErrorCode.SOCIAL_009);
        }

        String prefix = escapeLike(trimmed.toLowerCase(Locale.ROOT)) + "%";
        List<MemberProfileResponse> results = new ArrayList<>();
        memberRepository.searchFriendsByNicknamePrefix(viewerId, prefix, PageRequest.of(0, MAX_RESULTS))
                .forEach(member -> results.add(toResponse(member, FollowStatus.FRIEND, null)));
        int remaining = MAX_RESULTS - results.size();
        if (remaining > 0) {
            List<Member> others = memberRepository.searchNonFriendsByNicknamePrefix(
                    viewerId, prefix, PageRequest.of(0, remaining));
            Map<Long, FollowRequest> requests = requestsWith(viewerId, others.stream().map(Member::getId).toList());
            others.forEach(member -> results.add(nonFriendResponse(member, requests.get(member.getId()), viewerId)));
        }
        return results;
    }

    /**
     * 회원 프로필과 나와의 관계 (#153). 친구 아카이브 블러 화면에서 "친구 요청 / 요청함" 버튼을 그릴 때 쓴다.
     * 탈퇴했거나 없는 회원이면 SOCIAL_001. 친구가 아닌 회원 조회만 요청 수를 센다(SOCIAL_009) — 나·친구는 이미 아는 사이라.
     */
    public MemberProfileResponse getProfile(Long viewerId, Long memberId) {
        Member member = memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.SOCIAL_001));
        if (memberId.equals(viewerId)) {
            return toResponse(member, FollowStatus.SELF, null);
        }
        if (followRelationRepository.findBetween(viewerId, memberId).isPresent()) {
            return toResponse(member, FollowStatus.FRIEND, null);
        }
        if (!rateLimiter.tryAcquireProfileLookup(viewerId)) {
            throw new CustomException(ErrorCode.SOCIAL_009);
        }
        return nonFriendResponse(member, requestsWith(viewerId, List.of(memberId)).get(memberId), viewerId);
    }

    /**
     * 나와 다른 회원들 사이의 친구 요청 (상대 회원 ID → 요청). 교차 요청이 동시에 들어와 양쪽 요청이 다 남은 경우
     * 내가 보낸 요청을 쓴다 (검색과 프로필 조회가 같은 상태를 보이도록).
     */
    private Map<Long, FollowRequest> requestsWith(Long viewerId, List<Long> otherIds) {
        if (otherIds.isEmpty()) {
            return Map.of();
        }
        return followRequestRepository.findBetweenMemberAnd(viewerId, otherIds).stream()
                .collect(Collectors.toMap(r -> otherMemberId(r, viewerId), Function.identity(),
                        (a, b) -> a.isSentBy(viewerId) ? a : b));
    }

    /** 친구가 아닌 회원: 요청이 없으면 NONE, 내가 보냈으면 REQUESTED, 받았으면 RECEIVED. */
    private static MemberProfileResponse nonFriendResponse(Member member, FollowRequest request, Long viewerId) {
        if (request == null) {
            return toResponse(member, FollowStatus.NONE, null);
        }
        FollowStatus status = request.isSentBy(viewerId) ? FollowStatus.REQUESTED : FollowStatus.RECEIVED;
        return toResponse(member, status, request.getId());
    }

    private static Long otherMemberId(FollowRequest request, Long viewerId) {
        return request.isSentBy(viewerId) ? request.getReceiverId() : request.getRequesterId();
    }

    private static MemberProfileResponse toResponse(Member member, FollowStatus status, Long followRequestId) {
        return new MemberProfileResponse(member.getId(), member.getNickname(), member.getProfileImageUrl(),
                status, followRequestId);
    }

    /** 닉네임에 _나 %가 들어갈 수 있어서, 검색어의 와일드카드 문자를 글자 그대로 찾도록 바꾼다. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
