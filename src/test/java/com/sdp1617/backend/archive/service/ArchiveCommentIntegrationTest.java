package com.sdp1617.backend.archive.service;

import com.sdp1617.backend.archive.dto.ArchiveCommentCreateRequest;
import com.sdp1617.backend.archive.dto.ArchiveCommentPageResponse;
import com.sdp1617.backend.archive.dto.ArchiveCommentResponse;
import com.sdp1617.backend.archive.dto.ArchiveCommentUpdateRequest;
import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.global.error.CustomException;
import com.sdp1617.backend.global.error.ErrorCode;
import com.sdp1617.backend.heartcard.dto.HeartCardKokRequest;
import com.sdp1617.backend.heartcard.service.HeartCardKokService;
import com.sdp1617.backend.letter.dto.LetterCardRequest;
import com.sdp1617.backend.letter.dto.LetterEnvelopeRequest;
import com.sdp1617.backend.letter.entity.DesignType;
import com.sdp1617.backend.letter.service.LetterCardContentResolver;
import com.sdp1617.backend.letter.service.LetterInboxService;
import com.sdp1617.backend.letter.service.LetterWriteService;
import com.sdp1617.backend.mypage.service.AccountSettingsService;
import com.sdp1617.backend.notification.dto.NotificationResponse;
import com.sdp1617.backend.notification.entity.NotificationType;
import com.sdp1617.backend.notification.service.NotificationService;
import com.sdp1617.backend.social.entity.FollowRelation;
import com.sdp1617.backend.social.service.FollowService;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 아카이브 카드 댓글(인스타식)의 권한·답글·알림·정리를 실제 DB로 검증한다 (#164). */
@SpringBootTest
@Transactional
class ArchiveCommentIntegrationTest {

    @Autowired private EntityManager em;
    @Autowired private LetterWriteService letterWriteService;
    @Autowired private LetterInboxService letterInboxService;
    @Autowired private LetterCardContentResolver contentResolver;
    @Autowired private HeartCardKokService kokService;
    @Autowired private ArchiveCommentService commentService;
    @Autowired private ArchiveService archiveService;
    @Autowired private FollowService followService;
    @Autowired private NotificationService notificationService;
    @Autowired private AccountSettingsService accountSettingsService;

    private Member sender;
    /** 카드 주인 = 편지를 받아 콕한 사람 */
    private Member owner;
    private Member friend;
    private Member otherFriend;
    private Member stranger;
    private Long letterCardId;
    private Long archiveCardId;

    private Member member(String name) {
        Member member = new Member(name + "@comment.test", "encoded", name, Consent.requiredOnly());
        em.persist(member);
        return member;
    }

    private void assertError(ErrorCode expected, Executable executable) {
        assertEquals(expected, assertThrows(CustomException.class, executable).getErrorCode());
    }

    private ArchiveCommentResponse comment(Member author, String content) {
        return commentService.createComment(author.getId(), archiveCardId, new ArchiveCommentCreateRequest(content, null));
    }

    private ArchiveCommentResponse reply(Member author, Long parentCommentId, String content) {
        return commentService.createComment(author.getId(), archiveCardId,
                new ArchiveCommentCreateRequest(content, parentCommentId));
    }

    private List<ArchiveCommentResponse> comments(Member viewer) {
        em.flush();
        return commentService.getComments(viewer.getId(), archiveCardId, 0, 20).comments();
    }

    private List<NotificationResponse> notifications(Member member) {
        em.flush();
        return notificationService.getNotifications(member.getId()).stream()
                .filter(n -> n.type() == NotificationType.COMMENT).toList();
    }

    /** 보낸 사람이 주인에게 카드 1장짜리 편지를 보내고 주인이 콕한다. [편지 카드 ID, 아카이브 카드 ID] */
    private Long[] sendAndKok(String content) {
        Long letterId = letterWriteService.start(sender.getId(),
                new LetterEnvelopeRequest("은우", "티키", DesignType.DesignType_A)).letterId();
        Long cardId = letterWriteService.addCard(sender.getId(), letterId, contentResolver.resolve(sender.getId(),
                new LetterCardRequest(ArchiveCategory.MUSIC, null, content, null, null, null))).cards().get(0).cardId();
        letterWriteService.complete(sender.getId(), letterId);
        letterInboxService.send(sender.getId(), letterId, owner.getId());
        Long archiveId = kokService.updateKok(owner.getId(), cardId,
                new HeartCardKokRequest(true, ArchiveCategory.MUSIC)).archiveCardId();
        return new Long[] {cardId, archiveId};
    }

    @BeforeEach
    void setUp() {
        sender = member("cmtsender");
        owner = member("cmtowner");
        friend = member("cmtfriend");
        otherFriend = member("cmtother");
        stranger = member("cmtstranger");
        em.persist(FollowRelation.of(owner.getId(), friend.getId()));
        em.persist(FollowRelation.of(owner.getId(), otherFriend.getId()));

        Long[] ids = sendAndKok("출근길 노래");
        letterCardId = ids[0];
        archiveCardId = ids[1];
    }

    @Test
    void 카드_주인과_친구는_댓글을_쓰고_보고_친구가_아니면_카드가_없는_것과_같다() {
        comment(owner, "내 카드");
        comment(friend, "  나도 이 노래 좋아  ");

        List<ArchiveCommentResponse> list = comments(otherFriend);
        assertEquals(List.of("내 카드", "나도 이 노래 좋아"), list.stream().map(ArchiveCommentResponse::content).toList());
        assertEquals("cmtfriend", list.get(1).author().nickname());
        assertFalse(list.get(1).mine());
        assertEquals(2, archiveService.getCard(friend.getId(), archiveCardId).commentCount());

        assertError(ErrorCode.ARCHIVE_002, () -> comments(stranger));
        assertError(ErrorCode.ARCHIVE_002, () -> comment(stranger, "몰래"));
    }

    @Test
    void 답글은_한_단계만이고_답글에_답하면_같은_원댓글_아래로_간다() {
        ArchiveCommentResponse parent = comment(friend, "좋다");
        ArchiveCommentResponse firstReply = reply(owner, parent.commentId(), "@cmtfriend 고마워");
        ArchiveCommentResponse replyToReply = reply(otherFriend, firstReply.commentId(), "@cmtowner 나도");

        assertEquals(parent.commentId(), replyToReply.parentCommentId());
        List<ArchiveCommentResponse> top = comments(owner);
        assertEquals(1, top.size());
        assertEquals(2, top.get(0).replyCount());

        ArchiveCommentPageResponse replies = commentService.getReplies(owner.getId(), archiveCardId, parent.commentId(), 0, 20);
        assertEquals(List.of(firstReply.commentId(), replyToReply.commentId()),
                replies.comments().stream().map(ArchiveCommentResponse::commentId).toList());
        assertError(ErrorCode.ARCHIVE_004, () ->
                commentService.getReplies(owner.getId(), archiveCardId, firstReply.commentId(), 0, 20));
    }

    @Test
    void 다른_카드의_댓글이나_없는_댓글에는_답글을_달_수_없다() {
        Long otherArchiveCardId = sendAndKok("다른 카드")[1];
        Long otherCardComment = commentService.createComment(friend.getId(), otherArchiveCardId,
                new ArchiveCommentCreateRequest("다른 카드 댓글", null)).commentId();

        assertError(ErrorCode.ARCHIVE_004, () -> reply(friend, otherCardComment, "엉뚱한 카드"));
        assertError(ErrorCode.ARCHIVE_004, () -> reply(friend, -1L, "없는 댓글"));
        assertTrue(comments(owner).isEmpty());
    }

    @Test
    void 비공개_카드의_댓글은_주인만_본다() {
        comment(friend, "좋다");
        kokService.updateKok(owner.getId(), letterCardId, new HeartCardKokRequest(true, null, true));
        em.flush();

        assertEquals(1, comments(owner).size());
        assertError(ErrorCode.ARCHIVE_002, () -> comments(friend));
        assertError(ErrorCode.ARCHIVE_002, () -> comment(friend, "다시"));
    }

    @Test
    void 수정은_작성자만_삭제는_작성자와_카드_주인이_하고_원댓글을_지우면_답글도_지워진다() {
        ArchiveCommentResponse friendComment = comment(friend, "좋다");
        reply(otherFriend, friendComment.commentId(), "나도");

        assertError(ErrorCode.COMMON_004, () -> commentService.updateComment(owner.getId(), friendComment.commentId(),
                new ArchiveCommentUpdateRequest("주인이 고침")));
        ArchiveCommentResponse updated = commentService.updateComment(friend.getId(), friendComment.commentId(),
                new ArchiveCommentUpdateRequest("진짜 좋다"));
        assertTrue(updated.edited());
        assertEquals(1, updated.replyCount());

        ArchiveCommentResponse otherComment = comment(otherFriend, "나도 한마디");
        assertError(ErrorCode.COMMON_004, () -> commentService.deleteComment(friend.getId(), otherComment.commentId()));
        assertError(ErrorCode.ARCHIVE_004, () -> commentService.deleteComment(stranger.getId(), otherComment.commentId()));

        assertTrue(comments(owner).get(0).deletable());
        commentService.deleteComment(owner.getId(), friendComment.commentId());
        em.flush();
        em.clear();

        assertEquals(List.of(otherComment.commentId()),
                comments(owner).stream().map(ArchiveCommentResponse::commentId).toList());
        assertEquals(1, archiveService.getCard(owner.getId(), archiveCardId).commentCount());
    }

    @Test
    void 친구를_끊으면_보거나_고칠_수_없지만_내_댓글은_지울_수_있다() {
        ArchiveCommentResponse friendComment = comment(friend, "좋다");
        followService.unfollow(friend.getId(), owner.getId());
        em.flush();

        assertError(ErrorCode.ARCHIVE_002, () -> comments(friend));
        assertError(ErrorCode.ARCHIVE_004, () -> commentService.updateComment(friend.getId(), friendComment.commentId(),
                new ArchiveCommentUpdateRequest("고침")));
        assertEquals(1, comments(owner).size());

        commentService.deleteComment(friend.getId(), friendComment.commentId());
        assertTrue(comments(owner).isEmpty());
    }

    @Test
    void 남이_단_댓글은_카드_주인에게_답글은_원댓글_작성자에게_알림이_간다() {
        comment(owner, "내가 쓴 건 알림 없음");
        assertTrue(notifications(owner).isEmpty());

        ArchiveCommentResponse friendComment = comment(friend, "좋다");
        List<NotificationResponse> toOwner = notifications(owner);
        assertEquals(1, toOwner.size());
        assertEquals("cmtfriend님이 내 카드에 댓글을 남겼어요.", toOwner.get(0).content());
        assertEquals(archiveCardId, toOwner.get(0).archiveCardId());
        assertEquals(friend.getId(), toOwner.get(0).actorMemberId());
        assertNull(toOwner.get(0).letterId());

        reply(owner, friendComment.commentId(), "고마워");
        assertEquals(List.of("cmtowner님이 내 댓글에 답글을 남겼어요."),
                notifications(friend).stream().map(NotificationResponse::content).toList());

        reply(otherFriend, friendComment.commentId(), "나도");
        assertEquals(2, notifications(owner).size());
        assertEquals(2, notifications(friend).size());

        ArchiveCommentResponse ownerComment = comment(owner, "다들 고마워");
        reply(friend, ownerComment.commentId(), "천만에");
        assertEquals("cmtfriend님이 내 댓글에 답글을 남겼어요.", notifications(owner).get(0).content());
        assertEquals(3, notifications(owner).size());
    }

    @Test
    void 답글_알림은_실제로_답한_댓글의_작성자에게_가고_카드를_볼_수_없게_된_사람에게는_안_간다() {
        ArchiveCommentResponse root = comment(friend, "좋다");
        ArchiveCommentResponse otherReply = reply(otherFriend, root.commentId(), "나도");
        reply(owner, otherReply.commentId(), "@cmtother 고마워");

        assertEquals(List.of("cmtowner님이 내 댓글에 답글을 남겼어요."),
                notifications(otherFriend).stream().map(NotificationResponse::content).toList());
        assertEquals(1, notifications(friend).size()); // otherFriend가 단 답글 알림만

        followService.unfollow(friend.getId(), owner.getId());
        em.flush();
        reply(owner, root.commentId(), "@cmtfriend 잘 가");
        assertEquals(1, notifications(friend).size());
    }

    @Test
    void 콕을_취소하면_카드와_댓글이_함께_지워진다() {
        comment(friend, "좋다");
        kokService.updateKok(owner.getId(), letterCardId, new HeartCardKokRequest(false, null));
        em.flush();

        assertEquals(0L, em.createQuery("select count(c) from ArchiveCardComment c", Long.class).getSingleResult());
    }

    @Test
    void 탈퇴하면_남긴_댓글은_익명으로_남고_탈퇴한_주인의_카드_댓글은_지워진다() {
        comment(friend, "좋다");
        accountSettingsService.withdraw(friend.getId());
        em.flush();
        em.clear();

        ArchiveCommentResponse anonymous = comments(owner).get(0);
        assertEquals("좋다", anonymous.content());
        assertTrue(anonymous.author().nickname().startsWith(Member.WITHDRAWN_NICKNAME_PREFIX));

        accountSettingsService.withdraw(owner.getId());
        em.flush();
        assertEquals(0L, em.createQuery("select count(c) from ArchiveCardComment c", Long.class).getSingleResult());
    }
}
