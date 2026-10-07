package com.sdp1617.backend.social.service;

import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.social.dto.MemberSearchResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 닉네임(아이디)으로 회원 찾기 (LW-820, FR-030). 앞부분이 일치하는 회원을 친구 먼저 최대 20명까지 보여준다.
 * 닉네임은 로그인 아이디이기도 해서, 2자 이상부터 검색하고 결과 수·요청 수를 제한해 아이디 목록을 대량으로 모으지 못하게 한다.
 * 나 자신과 탈퇴한 회원은 나오지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberSearchService {

    static final int MIN_KEYWORD_LENGTH = 2;
    static final int MAX_RESULTS = 20;

    private final MemberRepository memberRepository;
    private final MemberSearchRateLimiter rateLimiter;

    public List<MemberSearchResponse> searchByNickname(Long viewerId, String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.length() < MIN_KEYWORD_LENGTH) {
            throw new CustomException(ErrorCode.SOCIAL_008);
        }
        if (!rateLimiter.tryAcquire(viewerId)) {
            throw new CustomException(ErrorCode.SOCIAL_009);
        }

        String prefix = escapeLike(trimmed.toLowerCase(Locale.ROOT)) + "%";
        List<MemberSearchResponse> results = new ArrayList<>();
        memberRepository.searchFriendsByNicknamePrefix(viewerId, prefix, PageRequest.of(0, MAX_RESULTS))
                .forEach(member -> results.add(toResponse(member, true)));
        int remaining = MAX_RESULTS - results.size();
        if (remaining > 0) {
            memberRepository.searchNonFriendsByNicknamePrefix(viewerId, prefix, PageRequest.of(0, remaining))
                    .forEach(member -> results.add(toResponse(member, false)));
        }
        return results;
    }

    private static MemberSearchResponse toResponse(Member member, boolean friend) {
        return new MemberSearchResponse(member.getId(), member.getNickname(), member.getProfileImageUrl(), friend);
    }

    /** 닉네임에 _나 %가 들어갈 수 있어서, 검색어의 와일드카드 문자를 글자 그대로 찾도록 바꾼다. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
