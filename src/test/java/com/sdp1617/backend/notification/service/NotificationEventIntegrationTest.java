package com.sdp1617.backend.notification.service;

import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.heartcard.dto.HeartCardKokRequest;
import com.sdp1617.backend.heartcard.service.HeartCardKokService;
import com.sdp1617.backend.letter.dto.DoodlePickRequest;
import com.sdp1617.backend.letter.dto.LetterCardRequest;
import com.sdp1617.backend.letter.dto.LetterEnvelopeRequest;
import com.sdp1617.backend.letter.dto.LetterResponse;
import com.sdp1617.backend.letter.entity.DesignType;
import com.sdp1617.backend.letter.service.LetterCardContentResolver;
import com.sdp1617.backend.letter.service.LetterInboxService;
import com.sdp1617.backend.letter.service.LetterWriteService;
import com.sdp1617.backend.notification.dto.NotificationResponse;
import com.sdp1617.backend.notification.entity.NotificationType;
import com.sdp1617.backend.social.repository.FollowRequestRepository;
import com.sdp1617.backend.social.service.FollowService;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 각 기능에서 알림함(INBOX) 알림이 만들어지는지 실제 DB로 검증한다 (#88). */
@SpringBootTest
@Transactional
class NotificationEventIntegrationTest {

    @Autowired private EntityManager em;
    @Autowired private LetterWriteService letterWriteService;
    @Autowired private LetterInboxService letterInboxService;
    @Autowired private LetterCardContentResolver contentResolver;
    @Autowired private HeartCardKokService kokService;
    @Autowired private FollowService followService;
    @Autowired private FollowRequestRepository followRequestRepository;
    @Autowired private NotificationService notificationService;

    private Member sender;
    private Member recipient;
    private Long letterId;
    private Long cardId;
    private List<Long> giftItemIds;

    private Member member(String name) {
        Member member = new Member(name + "@notify.test", "encoded", name, Consent.requiredOnly());
        em.persist(member);
        return member;
    }

    private List<NotificationResponse> notifications(Member member) {
        em.flush();
        return notificationService.getNotifications(member.getId());
    }

    /** 봉투: 받는 사람 "은우", 보내는 사람 "티키". 카드 1장과 선물 후보 2개를 담아 완료만 해 둔다. */
    @BeforeEach
    void setUp() {
        sender = member("notifysender");
        recipient = member("notifyrecipient");
        letterId = letterWriteService.start(sender.getId(),
                new LetterEnvelopeRequest("은우", "티키", DesignType.DesignType_A)).letterId();
        cardId = letterWriteService.addCard(sender.getId(), letterId, contentResolver.resolve(sender.getId(),
                new LetterCardRequest(ArchiveCategory.MUSIC, null, "출근길마다 듣는 노래야", null, null, null)))
                .cards().get(0).cardId();
        LetterResponse withPick = letterWriteService.replaceDoodlePick(sender.getId(), letterId,
                new DoodlePickRequest("이유", List.of("향초", "목베개")));
        giftItemIds = withPick.doodlePick().giftItems().stream().map(item -> item.giftItemId()).toList();
        letterWriteService.complete(sender.getId(), letterId);
    }

    @Test
    void 편지를_보내면_받는_사람에게_도착_알림이_간다() {
        letterInboxService.send(sender.getId(), letterId, recipient.getId());

        List<NotificationResponse> received = notifications(recipient);
        assertEquals(1, received.size());
        NotificationResponse notification = received.get(0);
        assertEquals(NotificationType.LETTER, notification.type());
        assertEquals("티키님에게서 편지가 도착했어요.", notification.content());
        assertEquals(sender.getId(), notification.actorMemberId());
        assertEquals(letterId, notification.letterId());
        assertNull(notification.cardId());
        assertTrue(notifications(sender).isEmpty());
    }

    @Test
    void 콕은_알림이_없다() {
        letterInboxService.send(sender.getId(), letterId, recipient.getId());

        kokService.updateKok(recipient.getId(), cardId, new HeartCardKokRequest(true, ArchiveCategory.MUSIC));

        assertTrue(notifications(sender).isEmpty());
    }

    @Test
    void 선물을_새로_고르거나_바꿀_때만_알림이_가고_취소나_같은_선물은_알림이_없다() {
        letterInboxService.send(sender.getId(), letterId, recipient.getId());

        letterInboxService.selectGift(recipient.getId(), letterId, giftItemIds.get(0));
        letterInboxService.selectGift(recipient.getId(), letterId, giftItemIds.get(0));
        letterInboxService.selectGift(recipient.getId(), letterId, giftItemIds.get(1));
        letterInboxService.selectGift(recipient.getId(), letterId, null);

        List<NotificationResponse> received = notifications(sender);
        assertEquals(2, received.size());
        assertTrue(received.stream().allMatch(n -> n.type() == NotificationType.GIFT_SELECTED
                && n.content().equals("은우님이 선물을 골랐어요.")
                && letterId.equals(n.letterId())));
    }

    @Test
    void 받는_사람이_편지를_지우면_그_편지로_받은_알림도_지워지고_보낸_사람의_알림은_남는다() {
        letterInboxService.send(sender.getId(), letterId, recipient.getId());
        Long otherLetterId = letterWriteService.start(sender.getId(),
                new LetterEnvelopeRequest("은우", "티키", DesignType.DesignType_A)).letterId();
        letterWriteService.addCard(sender.getId(), otherLetterId, contentResolver.resolve(sender.getId(),
                new LetterCardRequest(ArchiveCategory.MUSIC, null, "다른 편지", null, null, null)));
        letterWriteService.complete(sender.getId(), otherLetterId);
        letterInboxService.send(sender.getId(), otherLetterId, recipient.getId());
        letterInboxService.selectGift(recipient.getId(), letterId, giftItemIds.get(0));
        assertEquals(2, notifications(recipient).size());

        letterInboxService.deleteOrHide(recipient.getId(), letterId);

        List<NotificationResponse> remaining = notifications(recipient);
        assertEquals(1, remaining.size());
        assertEquals(otherLetterId, remaining.get(0).letterId());
        assertEquals(List.of(NotificationType.GIFT_SELECTED),
                notifications(sender).stream().map(NotificationResponse::type).toList());
    }

    @Test
    void 보낸_사람이_탈퇴했으면_선물을_골라도_알림을_만들지_않는다() {
        letterInboxService.send(sender.getId(), letterId, recipient.getId());
        sender.withdraw();
        em.flush();

        letterInboxService.selectGift(recipient.getId(), letterId, giftItemIds.get(0));

        assertTrue(notifications(sender).isEmpty());
    }

    @Test
    void 친구_요청과_수락_알림() {
        followService.sendFollowRequest(sender.getId(), recipient.getId());

        List<NotificationResponse> requested = notifications(recipient);
        assertEquals(1, requested.size());
        assertEquals(NotificationType.FOLLOW_REQUEST, requested.get(0).type());
        assertEquals("notifysender님이 친구 요청을 보냈어요.", requested.get(0).content());
        assertEquals(sender.getId(), requested.get(0).actorMemberId());

        Long requestId = followRequestRepository.findByRequesterIdAndReceiverId(sender.getId(), recipient.getId())
                .orElseThrow().getId();
        followService.acceptFollowRequest(recipient.getId(), requestId);

        List<NotificationResponse> accepted = notifications(sender);
        assertEquals(1, accepted.size());
        assertEquals(NotificationType.FOLLOW_ACCEPTED, accepted.get(0).type());
        assertEquals("notifyrecipient님과 친구가 되었어요.", accepted.get(0).content());
        assertEquals(recipient.getId(), accepted.get(0).actorMemberId());
        assertEquals(1, notifications(recipient).size());
    }

    @Test
    void 서로_요청해서_바로_친구가_되면_먼저_요청한_사람에게_수락_알림이_간다() {
        followService.sendFollowRequest(sender.getId(), recipient.getId());
        followService.sendFollowRequest(recipient.getId(), sender.getId());

        List<NotificationResponse> toSender = notifications(sender);
        assertEquals(1, toSender.size());
        assertEquals(NotificationType.FOLLOW_ACCEPTED, toSender.get(0).type());
        assertEquals(recipient.getId(), toSender.get(0).actorMemberId());
        assertEquals(List.of(NotificationType.FOLLOW_REQUEST),
                notifications(recipient).stream().map(NotificationResponse::type).toList());
    }
}
