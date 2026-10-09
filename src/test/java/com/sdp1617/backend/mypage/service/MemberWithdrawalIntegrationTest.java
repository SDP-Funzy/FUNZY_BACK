package com.sdp1617.backend.mypage.service;

import com.sdp1617.backend.archive.entity.ArchiveCard;
import com.sdp1617.backend.archive.entity.ArchiveCardLike;
import com.sdp1617.backend.archive.entity.ArchiveCategory;
import com.sdp1617.backend.auth.entity.AuthProvider;
import com.sdp1617.backend.auth.entity.Consent;
import com.sdp1617.backend.auth.entity.Member;
import com.sdp1617.backend.auth.entity.SocialConnection;
import com.sdp1617.backend.letter.dto.CardBoxType;
import com.sdp1617.backend.letter.entity.DesignType;
import com.sdp1617.backend.letter.dto.CardStorageResponse;
import com.sdp1617.backend.letter.entity.Letter;
import com.sdp1617.backend.letter.service.LetterCardBoxService;
import com.sdp1617.backend.letter.entity.LetterCardContent;
import com.sdp1617.backend.letter.entity.LetterInteraction;
import com.sdp1617.backend.letter.entity.LetterInteractionType;
import com.sdp1617.backend.notification.entity.Notification;
import com.sdp1617.backend.notification.entity.NotificationType;
import com.sdp1617.backend.social.entity.FollowRelation;
import com.sdp1617.backend.social.entity.FollowRequest;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 탈퇴를 실제 DB(외래키 포함)로 검증한다. 단위 테스트는 mock이라 "회원 행 삭제가 FK 위반으로 실패"하던 문제를 잡지 못했다.
 */
@SpringBootTest
@Transactional
class MemberWithdrawalIntegrationTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private AccountSettingsService accountSettingsService;

    @Autowired
    private LetterCardBoxService cardService;

    private Member member(String name) {
        Member member = new Member(name + "@withdraw.test", "encoded", name, Consent.requiredOnly());
        em.persist(member);
        return member;
    }

    private long count(String jpql, Long memberId) {
        return em.createQuery(jpql, Long.class).setParameter("id", memberId).getSingleResult();
    }

    @Test
    void 소셜_연결과_주고받은_카드가_있는_회원도_탈퇴되고_카드는_익명으로_남는다() {
        Member me = member("withdrawer");
        Member friend = member("friend");
        Member other = member("other");
        em.persist(SocialConnection.create(me, AuthProvider.KAKAO, "kakao-withdraw-1"));

        // 내가 친구에게 보낸 편지 (편지는 회원을 외래키로 참조한다)
        Letter sentLetter = Letter.start(me, "친구", "나", DesignType.values()[0]);
        sentLetter.addCard(new LetterCardContent(ArchiveCategory.values()[0], null, "보낸 카드",
                null, null, null, null));
        sentLetter.complete();
        sentLetter.sendTo(friend);
        em.persist(sentLetter);

        em.persist(FollowRelation.of(me.getId(), friend.getId()));
        em.persist(new FollowRequest(me.getId(), other.getId()));

        ArchiveCard myArchive = new ArchiveCard(me.getId(), 100L, ArchiveCategory.values()[0]);
        em.persist(myArchive);
        em.persist(new ArchiveCardLike(myArchive.getId(), friend.getId()));
        ArchiveCard friendArchive = new ArchiveCard(friend.getId(), 200L, ArchiveCategory.values()[0]);
        friendArchive.increaseLikeCount();
        em.persist(friendArchive);
        em.persist(new ArchiveCardLike(friendArchive.getId(), me.getId()));

        em.persist(new Notification(me.getId(), NotificationType.values()[0], "알림"));
        em.persist(new LetterInteraction(1L, me.getId(), LetterInteractionType.FAVORITE, "FAVORITE"));
        em.persist(new LetterInteraction(1L, me.getId(), LetterInteractionType.COMMENT, "남긴 댓글"));
        Letter unsentLetter = Letter.start(me, "친구", "나", DesignType.values()[0]);
        unsentLetter.addCard(new LetterCardContent(ArchiveCategory.values()[0], null, "보내지 않은 카드",
                null, null, null, null));
        em.persist(unsentLetter);
        Letter receivedLetter = Letter.start(friend, "나", "친구", DesignType.values()[0]);
        receivedLetter.addCard(new LetterCardContent(ArchiveCategory.values()[0], null, "받은 카드",
                null, null, null, null));
        receivedLetter.complete();
        receivedLetter.sendTo(me);
        em.persist(receivedLetter);
        em.flush();
        em.clear();

        accountSettingsService.withdraw(me.getId());
        em.flush();
        em.clear();

        // 회원 행은 남고 개인정보만 지워진다
        Member withdrawn = em.find(Member.class, me.getId());
        assertTrue(withdrawn.isWithdrawn());
        assertNull(withdrawn.getEmail());
        assertEquals(Member.WITHDRAWN_NICKNAME_PREFIX + me.getId(), withdrawn.getNickname());

        // 본인 전용 데이터와 다른 회원에게 영향을 주는 관계는 삭제
        assertEquals(0, count("select count(s) from SocialConnection s where s.member.id = :id", me.getId()));
        assertEquals(0, count("select count(f) from FollowRelation f where f.memberIdA = :id or f.memberIdB = :id", me.getId()));
        assertEquals(0, count("select count(r) from FollowRequest r where r.requesterId = :id or r.receiverId = :id", me.getId()));
        assertEquals(0, count("select count(a) from ArchiveCard a where a.ownerMemberId = :id", me.getId()));
        assertEquals(0, count("select count(l) from ArchiveCardLike l where l.memberId = :id", me.getId()));
        assertEquals(0, count("select count(n) from Notification n where n.memberId = :id", me.getId()));
        assertEquals(0, em.find(ArchiveCard.class, friendArchive.getId()).getLikeCount());
        assertEquals(1, count("select count(i) from LetterInteraction i where i.memberId = :id", me.getId()));
        // 보내지 않은 내 편지는 지우고, 친구에게 보낸 편지는 친구 보관함에 남긴다
        assertEquals(0, count("select count(l) from Letter l where l.sender.id = :id"
                + " and l.status <> com.sdp1617.backend.letter.entity.LetterStatus.SENT", me.getId()));
        assertEquals(1, count("select count(l) from Letter l where l.sender.id = :id"
                + " and l.status = com.sdp1617.backend.letter.entity.LetterStatus.SENT", me.getId()));
        // 받은 편지는 받은 편지함에서만 숨기고, 보낸 친구의 보낸 편지함에는 남긴다
        Letter hidden = em.find(Letter.class, receivedLetter.getId());
        assertNotNull(hidden.getRecipientHiddenAt());
        assertEquals(friend.getId(), hidden.getSender().getId());

        // 친구의 보관함에는 주고받은 카드가 "탈퇴한회원N"으로 남는다
        List<CardStorageResponse> friendReceived =
                cardService.getCards(friend.getId(), CardBoxType.RECEIVED, null, null, null, 20).items();
        assertEquals(1, friendReceived.size());
        assertEquals(Member.WITHDRAWN_NICKNAME_PREFIX + me.getId(), friendReceived.get(0).senderNickname());
        assertEquals(1, cardService.getCards(friend.getId(), CardBoxType.SENT, null, null, null, 20).items().size());

        // 같은 이메일·아이디로 다시 가입할 수 있다
        member("withdrawer");
        em.flush();
    }
}
