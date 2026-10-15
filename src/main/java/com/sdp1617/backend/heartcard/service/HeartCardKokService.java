package com.sdp1617.backend.heartcard.service;

import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.repository.ArchiveCardRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.letter.entity.LetterCard;
import com.sdp1617.backend.letter.service.ReceivedLetterAccess;
import com.sdp1617.backend.heartcard.dto.HeartCardKokRequest;
import com.sdp1617.backend.heartcard.dto.HeartCardKokResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HeartCardKokService {

    private final ArchiveCardRepository archiveCardRepository;
    private final ReceivedLetterAccess receivedLetterAccess;

    public HeartCardKokResponse getKok(Long memberId, Long heartCardId) {
        requireLogin(memberId);
        receivedLetterAccess.requireReceivedCard(memberId, heartCardId);
        return archiveCardRepository.findByOwnerMemberIdAndLetterCardId(memberId, heartCardId)
                .map(archiveCard -> HeartCardKokResponse.active(heartCardId, archiveCard))
                .orElseGet(() -> HeartCardKokResponse.inactive(heartCardId));
    }

    @Transactional
    public HeartCardKokResponse updateKok(Long memberId, Long heartCardId, HeartCardKokRequest request) {
        requireLogin(memberId);
        HeartCardKokRequest safeRequest = request == null ? new HeartCardKokRequest(true, null) : request;
        LetterCard card = receivedLetterAccess.lockReceivedCard(memberId, heartCardId);
        return archiveCardRepository.findByOwnerMemberIdAndLetterCardId(memberId, heartCardId)
                .map(archiveCard -> safeRequest.kokOrDefault()
                        ? keepKok(heartCardId, archiveCard, safeRequest)
                        : deleteKok(heartCardId, archiveCard))
                .orElseGet(() -> safeRequest.kokOrDefault()
                        ? createKok(memberId, card, safeRequest)
                        : HeartCardKokResponse.inactive(heartCardId));
    }

    private HeartCardKokResponse createKok(Long memberId, LetterCard card, HeartCardKokRequest request) {
        ArchiveCard archiveCard = archiveCardRepository.save(
                ArchiveCard.ofLetterCard(memberId, card, request.categoryOr(card.getCategory()),
                        Boolean.TRUE.equals(request.locked())));
        return HeartCardKokResponse.active(card.getId(), archiveCard);
    }

    /** 이미 콕한 카드는 그대로 두고, locked를 보냈으면 공개/비공개만 바꾼다 (편지 보관함·아카이브 양쪽에서 전환, #147). */
    private HeartCardKokResponse keepKok(Long heartCardId, ArchiveCard archiveCard, HeartCardKokRequest request) {
        if (request.locked() != null) {
            archiveCard.updateLocked(request.locked());
        }
        return HeartCardKokResponse.active(heartCardId, archiveCard);
    }

    private HeartCardKokResponse deleteKok(Long heartCardId, ArchiveCard archiveCard) {
        archiveCardRepository.delete(archiveCard);
        return HeartCardKokResponse.inactive(heartCardId);
    }

    private void requireLogin(Long memberId) {
        if (memberId == null) {
            throw new CustomException(ErrorCode.COMMON_003);
        }
    }
}
