package com.sdp1617.backend.letter.service;

import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.letter.dto.DoodlePickRequest;
import com.sdp1617.backend.letter.dto.LetterDraftResponse;
import com.sdp1617.backend.letter.dto.LetterEnvelopeRequest;
import com.sdp1617.backend.letter.dto.LetterResponse;
import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.entity.LetterCardContent;
import com.sdp1617.backend.letter.entity.LetterStatus;
import com.sdp1617.backend.letter.repository.LetterRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 편지 쓰기 (#82, #83): 봉투 구성 → 카드 1~5장 → 두들픽(선택) → 완료. 전송 전까지는 보낸 사람만 보고 고칠 수 있다.
 * 다른 사람의 편지는 존재 여부를 드러내지 않도록 없는 편지(LETTER_001)와 같이 응답한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LetterWriteService {

    private static final EnumSet<LetterStatus> UNSENT = EnumSet.of(LetterStatus.DRAFT, LetterStatus.COMPLETED);

    private final LetterRepository letterRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public LetterResponse start(Long memberId, LetterEnvelopeRequest request) {
        Letter letter = Letter.start(memberRepository.getReferenceById(memberId),
                request.toName(), request.fromName(), request.designType());
        return LetterResponse.from(letterRepository.save(letter));
    }

    /** 아직 보내지 않은(작성 중·완료) 내 편지 목록 — 이어쓰기 (#85). 최근 수정 순. */
    public List<LetterDraftResponse> getUnsentLetters(Long memberId) {
        return letterRepository.findBySender_IdAndStatusInOrderByUpdatedAtDescIdDesc(memberId, UNSENT).stream()
                .map(LetterDraftResponse::from)
                .toList();
    }

    @Transactional
    public LetterResponse updateEnvelope(Long memberId, Long letterId, LetterEnvelopeRequest request) {
        return modify(memberId, letterId,
                letter -> letter.updateEnvelope(request.toName(), request.fromName(), request.designType()));
    }

    /** 카드 내용은 {@link LetterCardContentResolver}로 S3 사진 확인까지 마친 뒤(트랜잭션 밖) 넘긴다. */
    @Transactional
    public LetterResponse addCard(Long memberId, Long letterId, LetterCardContent content) {
        return modify(memberId, letterId, letter -> letter.addCard(content));
    }

    @Transactional
    public LetterResponse updateCard(Long memberId, Long letterId, Long cardId, LetterCardContent content) {
        return modify(memberId, letterId, letter -> letter.updateCard(cardId, content));
    }

    @Transactional
    public LetterResponse removeCard(Long memberId, Long letterId, Long cardId) {
        return modify(memberId, letterId, letter -> letter.removeCard(cardId));
    }

    @Transactional
    public LetterResponse replaceDoodlePick(Long memberId, Long letterId, DoodlePickRequest request) {
        return modify(memberId, letterId, letter -> letter.replaceDoodlePick(request.reason(), request.giftNames()));
    }

    @Transactional
    public LetterResponse removeDoodlePick(Long memberId, Long letterId) {
        return modify(memberId, letterId, Letter::removeDoodlePick);
    }

    @Transactional
    public LetterResponse complete(Long memberId, Long letterId) {
        return modify(memberId, letterId, Letter::complete);
    }

    /**
     * 카드 삭제·사진 교체·편지 삭제로 빠진 사진은 여기서 S3에서 지우지 않는다. 같은 사진을 동시에 다른 카드에 넣는
     * 요청이 있으면 아직 쓰이는 사진을 지울 수 있어서, 쓰이지 않는 사진은 정기 정리(#122)에서 지운다.
     */
    private LetterResponse modify(Long memberId, Long letterId, Consumer<Letter> change) {
        Letter letter = findMyLetterForUpdate(memberId, letterId);
        change.accept(letter);
        letterRepository.flush();
        return LetterResponse.from(letter);
    }

    private Letter findMyLetterForUpdate(Long memberId, Long letterId) {
        return letterRepository.findByIdForUpdate(letterId)
                .filter(letter -> letter.isWrittenBy(memberId))
                .orElseThrow(() -> new CustomException(ErrorCode.LETTER_001));
    }
}
