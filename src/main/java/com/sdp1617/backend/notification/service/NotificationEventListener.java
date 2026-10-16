package com.sdp1617.backend.notification.service;

import com.sdp1617.backend.archive.service.ArchiveCommentCreatedEvent;
import com.sdp1617.backend.auth.repository.MemberRepository;
import com.sdp1617.backend.letter.service.GiftSelectedEvent;
import com.sdp1617.backend.letter.service.LetterSentEvent;
import com.sdp1617.backend.letter.service.ReceivedLetterHiddenEvent;
import com.sdp1617.backend.notification.entity.Notification;
import com.sdp1617.backend.notification.entity.NotificationType;
import com.sdp1617.backend.notification.repository.NotificationRepository;
import com.sdp1617.backend.social.service.FollowAcceptedEvent;
import com.sdp1617.backend.social.service.FollowRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 각 기능이 발행한 이벤트로 알림함(INBOX)에 알림을 만든다 (#88).
 * 발행한 요청과 같은 트랜잭션에서 저장해, 그 요청이 실패하면 알림도 남지 않는다.
 * 편지 알림의 이름은 봉투에 적힌 이름(받는 사람에게는 보내는 사람 이름, 보낸 사람에게는 받는 사람 이름)을 쓴다.
 * 알림 받을 회원이 탈퇴했으면 만들지 않는다. 받는 사람이 편지를 지우면 그 편지로 받은 알림도 지운다.
 * 만든 알림은 커밋된 뒤 웹 푸시로도 보낸다 ({@link PushNotificationDispatcher}, #89).
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void onLetterSent(LetterSentEvent event) {
        notify(event.recipientId(), NotificationType.LETTER, event.senderName() + "님에게서 편지가 도착했어요.",
                event.senderId(), event.letterId(), null);
    }

    @EventListener
    public void onGiftSelected(GiftSelectedEvent event) {
        notify(event.senderId(), NotificationType.GIFT_SELECTED, event.recipientName() + "님이 선물을 골랐어요.",
                event.recipientId(), event.letterId(), null);
    }

    /**
     * 아카이브 카드 댓글·답글 (#164). 남이 내 카드에 남기면 카드 주인에게, 내 댓글에 답글이 달리면 그 댓글 작성자에게.
     * 카드 주인이 답한 댓글의 작성자면 한 번만 보낸다.
     */
    @EventListener
    public void onArchiveCommentCreated(ArchiveCommentCreatedEvent event) {
        Long authorId = event.authorId();
        Long parentAuthorId = event.parentAuthorId();
        String name = event.authorNickname();
        if (!event.cardOwnerId().equals(authorId)) {
            String content = event.cardOwnerId().equals(parentAuthorId)
                    ? name + "님이 내 댓글에 답글을 남겼어요."
                    : name + "님이 내 카드에 댓글을 남겼어요.";
            notify(event.cardOwnerId(), NotificationType.COMMENT, content, authorId, null, event.archiveCardId());
        }
        if (parentAuthorId != null && !parentAuthorId.equals(authorId) && !parentAuthorId.equals(event.cardOwnerId())) {
            notify(parentAuthorId, NotificationType.COMMENT, name + "님이 내 댓글에 답글을 남겼어요.",
                    authorId, null, event.archiveCardId());
        }
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
                        requester.getNickname() + "님이 팔로우 요청을 보냈어요.", requester.getId(), null, null));
    }

    @EventListener
    public void onFollowAccepted(FollowAcceptedEvent event) {
        memberRepository.findActiveById(event.accepterId()).ifPresent(accepter ->
                notify(event.requesterId(), NotificationType.FOLLOW_ACCEPTED,
                        accepter.getNickname() + "님과 친구가 되었어요.", accepter.getId(), null, null));
    }

    /** 알림 받을 회원이 탈퇴했으면 만들지 않는다. */
    private void notify(Long memberId, NotificationType type, String content, Long actorMemberId, Long letterId,
                           Long archiveCardId) {
        if (memberRepository.findActiveById(memberId).isEmpty()) {
            return;
        }
        Notification notification = notificationRepository.save(
                new Notification(memberId, type, content, actorMemberId, letterId, archiveCardId));
        eventPublisher.publishEvent(NotificationCreatedEvent.from(notification));
    }
}
