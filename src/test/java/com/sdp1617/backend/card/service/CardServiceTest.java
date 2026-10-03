package com.sdp1617.backend.card.service;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.card.dto.DesignType;
import com.sdp1617.backend.card.dto.request.CardCreateRequest;
import com.sdp1617.backend.card.entity.Card;
import com.sdp1617.backend.card.entity.Envelop;
import com.sdp1617.backend.card.repository.CardRepository;
import com.sdp1617.backend.card.repository.EnvelopRepository;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.global.s3.S3ImageService;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    private static final Long SENDER_ID = 1L;
    private static final Long RECEIVER_ID = 2L;

    @Mock
    private EnvelopRepository envelopRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private S3ImageService s3ImageService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private CardService cardService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cardService, "frontendUrl", "https://funzy.kr");
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> action = invocation.getArgument(0);
            return action.doInTransaction(new SimpleTransactionStatus());
        });
        lenient().when(envelopRepository.save(any(Envelop.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(cardRepository.save(any(Card.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CardCreateRequest request(Long receiverId, String imageKey) {
        return new CardCreateRequest(receiverId, DesignType.values()[0], "제목", ArchiveCategory.values()[0],
                null, null, "내용", imageKey);
    }

    @Test
    void 보내는_사람은_로그인_사용자로_정해진다() {
        Member sender = mock(Member.class);
        when(envelopRepository.findBySender_IdAndReceiver_Id(SENDER_ID, RECEIVER_ID)).thenReturn(Optional.empty());
        when(memberRepository.existsById(RECEIVER_ID)).thenReturn(true);
        when(entityManager.getReference(Member.class, SENDER_ID)).thenReturn(sender);

        cardService.createCard(SENDER_ID, request(RECEIVER_ID, null));

        verify(envelopRepository).save(argThat(envelop -> envelop.getSender() == sender));
    }

    @Test
    void 본인에게는_보낼_수_없다() {
        CustomException exception = assertThrows(CustomException.class,
                () -> cardService.createCard(SENDER_ID, request(SENDER_ID, null)));

        assertEquals(ErrorCode.CARD_005, exception.getErrorCode());
        verify(cardRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_회원에게는_보낼_수_없다() {
        when(envelopRepository.findBySender_IdAndReceiver_Id(SENDER_ID, RECEIVER_ID)).thenReturn(Optional.empty());
        when(memberRepository.existsById(RECEIVER_ID)).thenReturn(false);

        CustomException exception = assertThrows(CustomException.class,
                () -> cardService.createCard(SENDER_ID, request(RECEIVER_ID, null)));

        assertEquals(ErrorCode.CARD_006, exception.getErrorCode());
        verify(envelopRepository, never()).save(any());
        verify(cardRepository, never()).save(any());
    }

    @Test
    void 이미_있는_봉투에는_수신자_확인_없이_카드를_추가한다() {
        Envelop envelop = mock(Envelop.class);
        when(envelopRepository.findBySender_IdAndReceiver_Id(SENDER_ID, RECEIVER_ID)).thenReturn(Optional.of(envelop));

        cardService.createCard(SENDER_ID, request(RECEIVER_ID, null));

        verify(memberRepository, never()).existsById(anyLong());
        verify(cardRepository).save(argThat(card -> card.getEnvelop() == envelop));
    }

    @Test
    void 이미지_소유권은_로그인_사용자_기준으로_검증한다() {
        doThrow(new CustomException(ErrorCode.COMMON_004))
                .when(s3ImageService).validateOwnership("cards/2/other.png", "cards/1/");

        CustomException exception = assertThrows(CustomException.class,
                () -> cardService.createCard(SENDER_ID, request(RECEIVER_ID, "cards/2/other.png")));

        assertEquals(ErrorCode.COMMON_004, exception.getErrorCode());
        verify(s3ImageService, never()).validateUploadedImage(anyString(), any(), any(), any());
        verify(cardRepository, never()).save(any());
    }
}
