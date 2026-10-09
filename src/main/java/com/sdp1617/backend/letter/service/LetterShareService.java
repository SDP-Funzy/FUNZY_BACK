package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.letter.dto.LetterResponse;
import com.sdp1617.backend.letter.dto.ShareLinkResponse;
import com.sdp1617.backend.letter.dto.SharedLetterResponse;
import com.sdp1617.backend.letter.dto.SharedLetterResponse.Viewer;
import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.repository.LetterRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공유 링크로 편지 보내기 (#87)와 링크로 받은 편지의 받는 사람 확정 (#114).
 * - 링크는 보낸 사람이 발급·재발급·취소하고, 발급 후 30일 동안 유효하다.
 * - 링크가 있으면 누구나(로그인 없이도) 읽기 전용으로 볼 수 있다. 반응·선물 고르기는 받는 사람으로 확정된 회원만.
 * - 받는 사람은 로그인한 회원이 링크로 '받기'를 하면 정해진다. 처음 받은 1명만 (편지 행을 잠가 순서대로 처리).
 * - 회원에게 직접 보낸 편지도 링크를 만들 수 있다. 이때 받는 사람은 그 회원으로 이미 정해져 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LetterShareService {

    static final Duration LINK_LIFETIME = Duration.ofDays(30);
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LetterRepository letterRepository;
    private final MemberRepository memberRepository;

    /** 링크 발급·재발급. 완료한 편지는 이때 보낸 편지가 된다. 내가 쓴 편지가 아니면 LETTER_001. */
    @Transactional
    public ShareLinkResponse issue(Long memberId, Long letterId) {
        Letter letter = lockMyLetter(memberId, letterId);
        LocalDateTime expiresAt = LocalDateTime.now().plus(LINK_LIFETIME);
        letter.issueShareLink(newToken(), expiresAt);
        return new ShareLinkResponse(letter.getShareToken(), expiresAt);
    }

    /** 링크 취소. 이미 받은 사람은 받은 편지함에서 계속 본다. */
    @Transactional
    public void revoke(Long memberId, Long letterId) {
        lockMyLetter(memberId, letterId).revokeShareLink();
    }

    /**
     * 링크로 편지 보기 (로그인 없이도 가능). viewerId는 로그인했을 때만 있다.
     * 보낸 사람·받는 사람이 아니면 받는 사람 정보·읽은 시각·고른 선물은 가린다.
     */
    public SharedLetterResponse view(String token, Long viewerId) {
        Letter letter = findByValidToken(token);
        Viewer viewer = viewerOf(letter, viewerId);
        LetterResponse body = viewer == Viewer.SENDER || viewer == Viewer.RECIPIENT
                ? LetterResponse.from(letter)
                : LetterResponse.forLinkViewer(letter);
        boolean receivable = (viewer == Viewer.OTHER && letter.isWaitingForRecipient())
                // 받은 편지함에서 지운 받는 사람은 다시 받아 되돌릴 수 있다
                || (viewer == Viewer.RECIPIENT && !letter.isVisibleToRecipient(viewerId));
        return new SharedLetterResponse(body, viewer, receivable);
    }

    /**
     * 링크로 받기: 로그인한 회원을 받는 사람으로 정한다. 같은 회원이 다시 받으면 그대로 성공하고,
     * 받은 편지함에서 지웠던 편지면 되돌린다. 보낸 사람 본인이면 LETTER_013, 이미 다른 회원이 받았으면 LETTER_012.
     */
    @Transactional
    public LetterResponse receive(String token, Long memberId) {
        // 편지를 먼저 읽지 않고 ID만 찾는다 — 그래야 아래 잠금 조회가 최신 값을 읽어 동시에 받은 회원을 덮어쓰지 않는다
        Long letterId = letterRepository.findIdByShareToken(token)
                .orElseThrow(() -> new CustomException(ErrorCode.LETTER_011));
        // 같은 링크로 동시에 받으면 행 잠금으로 순서대로 처리해 1명만 받는 사람이 된다
        // 잠근 뒤 링크를 다시 확인한다: 그 사이 취소·재발급됐으면 옛 링크로는 받을 수 없다
        Letter letter = letterRepository.findByIdForUpdate(letterId)
                .filter(locked -> token.equals(locked.getShareToken()))
                .filter(locked -> locked.isShareLinkValid(LocalDateTime.now()))
                .orElseThrow(() -> new CustomException(ErrorCode.LETTER_011));
        if (letter.isWrittenBy(memberId)) {
            throw new CustomException(ErrorCode.LETTER_013);
        }
        if (letter.isRecipient(memberId)) {
            letter.restoreForRecipient();
            return LetterResponse.from(letter);
        }
        if (letter.getRecipient() != null) {
            throw new CustomException(ErrorCode.LETTER_012);
        }
        Member recipient = memberRepository.findActiveById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_002));
        letter.receiveViaLink(recipient);
        return LetterResponse.from(letter);
    }

    private Letter lockMyLetter(Long memberId, Long letterId) {
        return letterRepository.findByIdForUpdate(letterId)
                .filter(letter -> letter.isWrittenBy(memberId))
                .orElseThrow(() -> new CustomException(ErrorCode.LETTER_001));
    }

    /** 없는·취소된·만료된 링크는 구분하지 않고 LETTER_011 (링크 존재 여부를 드러내지 않음). */
    private Letter findByValidToken(String token) {
        return letterRepository.findByShareToken(token)
                .filter(letter -> letter.isShareLinkValid(LocalDateTime.now()))
                .orElseThrow(() -> new CustomException(ErrorCode.LETTER_011));
    }

    private static Viewer viewerOf(Letter letter, Long viewerId) {
        if (viewerId == null) {
            return Viewer.ANONYMOUS;
        }
        if (letter.isWrittenBy(viewerId)) {
            return Viewer.SENDER;
        }
        if (letter.isRecipient(viewerId)) {
            return Viewer.RECIPIENT;
        }
        return Viewer.OTHER;
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
