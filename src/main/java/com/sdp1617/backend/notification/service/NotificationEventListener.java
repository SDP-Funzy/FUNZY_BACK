package com.sdp1617.backend.notification.service;

import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.letter.service.GiftSelectedEvent;
import com.sdp1617.backend.letter.service.LetterReactedEvent;
import com.sdp1617.backend.letter.service.LetterSentEvent;
import com.sdp1617.backend.letter.service.ReceivedLetterHiddenEvent;
import com.sdp1617.backend.notification.entity.Notification;
import com.sdp1617.backend.notification.entity.NotificationType;
import com.sdp1617.backend.notification.repository.NotificationRepository;
import com.sdp1617.backend.social.service.FollowAcceptedEvent;
import com.sdp1617.backend.social.service.FollowRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 각 기능이 발행한 이벤트로 알림함(INBOX)에 알림을 만든다 (#88).
 * 발행한 요청과 같은 트랜잭션에서 저장해, 그 요청이 실패하면 알림도 남지 않는다.
 * 편지 알림의 이름은 봉투에 적힌 이름(받는 사람에게는 보내는 사람 이름, 보낸 사람에게는 받는 사람 이름)을 쓴다.
 * 알림 받을 회원이 탈퇴했으면 만들지 않는다. 받는 사람이 편지를 지우면 그 편지로 받은 알림도 지운다.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;

    @EventListener
    public void onLetterSent(LetterSentEvent event) {
        notify(event.recipientId(), NotificationType.LETTER, event.senderName() + "님에게서 편지가 도착했어요.",
                event.senderId(), event.letterId(), null);
    }

    @EventListener
    public void onLetterReacted(LetterReactedEvent event) {
        String name = event.recipientName();
        boolean created = switch (event.kind()) {
            case CARD_EMOJI -> notifyEmojiOnce(event, name + "님이 마음카드에 이모지를 남겼어요.", event.cardId());
            case CARD_COMMENT -> notify(event.senderId(), NotificationType.COMMENT,
                    name + "님이 마음카드에 코멘트를 남겼어요.", event.recipientId(), event.letterId(), event.cardId());
            case GIFT_EMOJI -> notifyEmojiOnce(event, name + "님이 두들픽 선물에 이모지를 남겼어요.", null);
        };
        if (created) {
            event.markNotificationCreated();
        }
    }

    @EventListener
    public void onGiftSelected(GiftSelectedEvent event) {
        notify(event.senderId(), NotificationType.GIFT_SELECTED, event.recipientName() + "님이 선물을 골랐어요.",
                event.recipientId(), event.letterId(), null);
    }

    /** 받은 편지함에서 지운 편지는 열 수 없으니, 그 편지로 받은 알림(편지 도착)도 지운다. */
    @EventListener
    public void onReceivedLetterHidden(ReceivedLetterHiddenEvent event) {
        notificationRepository.deleteByMemberIdAndLetterId(event.recipientId(), event.letterId());
    }

    @EventListener
    public void onFollowRequested(FollowRequestedEvent event) {
        memberRepository.findActiveById(event.requesterId()).ifPresent(requester ->
                notify(event.receiverId(), NotificationType.FOLLOW_REQUEST,
                        requester.getNickname() + "님이 친구 요청을 보냈어요.", requester.getId(), null, null));
    }

    @EventListener
    public void onFollowAccepted(FollowAcceptedEvent event) {
        memberRepository.findActiveById(event.accepterId()).ifPresent(accepter ->
                notify(event.requesterId(), NotificationType.FOLLOW_ACCEPTED,
                        accepter.getNickname() + "님과 친구가 되었어요.", accepter.getId(), null, null));
    }

    /**
     * 이모지 알림은 마음카드마다(두들픽 선물 이모지는 편지마다) 한 번만 만든다 (LR-114, LR-614).
     * 같은 이모지를 다시 눌러 지웠다가 또 남기면 반응은 새로 생기지만, 그때마다 알림이 쌓이지 않게 한다.
     */
    private boolean notifyEmojiOnce(LetterReactedEvent event, String content, Long cardId) {
        if (notificationRepository.existsByMemberIdAndActorMemberIdAndTypeAndLetterIdAndCardId(
                event.senderId(), event.recipientId(), NotificationType.REACTION, event.letterId(), cardId)) {
            return false;
        }
        return notify(event.senderId(), NotificationType.REACTION, content, event.recipientId(), event.letterId(), cardId);
    }

    /** 알림을 만들었으면 true. 알림 받을 회원이 탈퇴했으면 만들지 않고 false. */
    private boolean notify(Long memberId, NotificationType type, String content, Long actorMemberId, Long letterId,
                           Long cardId) {
        if (memberRepository.findActiveById(memberId).isEmpty()) {
            return false;
        }
        notificationRepository.save(new Notification(memberId, type, content, actorMemberId, letterId, cardId));
        return true;
    }
}
